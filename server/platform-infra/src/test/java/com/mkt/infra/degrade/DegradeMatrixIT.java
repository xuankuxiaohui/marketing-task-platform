package com.mkt.infra.degrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.infra.RedisITSupport;
import com.mkt.infra.RedisPauseSupport;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.infra.outbox.AwaitOutboxDrain;
import com.mkt.infra.outbox.ConsumerDirection;
import com.mkt.infra.outbox.EventConsumer;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.infra.outbox.OutboxRelay;
import com.mkt.infra.outbox.OutboxRoutes;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.RedissonKeyValueStore;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redis pause applies design §6.8 row by row. Requires Docker; leave for CI when absent.
 */
@Testcontainers
class DegradeMatrixIT {

    @Container
    static final org.testcontainers.containers.GenericContainer<?> REDIS = RedisITSupport.redis();

    private RedissonClient client;

    @AfterEach
    void shutdown() {
        try {
            RedisPauseSupport.unpause(REDIS);
        } catch (RuntimeException ignored) {
            // already running
        }
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    void pauseRedisAppliesEveryMatrixRow() {
        assertThat(DegradeComponent.values()).hasSize(9);
        assertThat(DegradeMatrix.action(DegradeComponent.SESSION)).isEqualTo(DegradeAction.REJECT);
        assertThat(DegradeMatrix.action(DegradeComponent.RATE_LIMIT)).isEqualTo(DegradeAction.ALLOW);
        assertThat(DegradeMatrix.action(DegradeComponent.CACHE)).isEqualTo(DegradeAction.L1_OR_DB);
        assertThat(DegradeMatrix.action(DegradeComponent.DISTRIBUTED_LOCK)).isEqualTo(DegradeAction.SKIP_ROUND);
        assertThat(DegradeMatrix.action(DegradeComponent.CLAIM_LOCK)).isEqualTo(DegradeAction.FALLBACK_CAS);
        assertThat(DegradeMatrix.action(DegradeComponent.RISK)).isEqualTo(DegradeAction.POLICY);
        assertThat(DegradeMatrix.action(DegradeComponent.OUTBOX_RELAY)).isEqualTo(DegradeAction.SKIP_ROUND);
        assertThat(DegradeMatrix.action(DegradeComponent.NONCE)).isEqualTo(DegradeAction.REJECT);
        assertThat(DegradeMatrix.action(DegradeComponent.TRACKING)).isEqualTo(DegradeAction.ALLOW);

        client = RedisITSupport.client(REDIS);
        RedissonKeyValueStore store = RedisITSupport.store(client);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(store, Clock.systemUTC());
        NonceStore nonces = new NonceStore(store);
        SessionAvailability session = new SessionAvailability(store);
        PlatformLock locks = new PlatformLock(store);
        TwoLevelPlatformCache cache = new TwoLevelPlatformCache(store);
        MemoryOutboxStore outbox = new MemoryOutboxStore();
        AtomicInteger consumed = new AtomicInteger();
        EventConsumer audit = new EventConsumer() {
            @Override
            public ConsumerDirection direction() {
                return ConsumerDirection.SYS_AUDIT_LOG;
            }

            @Override
            public void consume(OutboxRecord row) {
                consumed.incrementAndGet();
            }
        };
        OutboxRelay relay = new OutboxRelay(outbox, OutboxProducer.ADMIN, locks, Clock.systemUTC(), List.of(audit));
        AwaitOutboxDrain drain = new AwaitOutboxDrain(outbox, OutboxProducer.ADMIN, relay);

        assertThat(limiter.tryAcquire(RateLimitDim.IP, "ok", 60, 5)).isTrue();
        assertThat(nonces.tryConsume("app", "n-ok", 60)).isTrue();
        session.requireAvailable();
        assertThat(locks.tryLock("sched:publish-scan")).isEqualTo(LockAcquire.ACQUIRED);
        locks.unlock("sched:publish-scan");
        CacheProbe warmed = cache.get(CacheNamespace.DICT, "warm", CacheProbe.class, () -> new CacheProbe("l1"));
        assertThat(warmed.value()).isEqualTo("l1");
        outbox.insert(OutboxRoutes.AUDIT_LOG, OutboxProducer.ADMIN.id(), "sys_audit_log", "1", "{}");
        assertThat(outbox.countPending(OutboxProducer.ADMIN.id())).isEqualTo(1);

        RedisPauseSupport.runPaused(REDIS, () -> {
            assertThat(limiter.tryAcquire(RateLimitDim.IP, "paused", 60, 1)).isTrue();
            assertThat(limiter.degradeEvents()).isGreaterThanOrEqualTo(1);
            assertThat(limiter.tryAcquire(RateLimitDim.IP, "track-paused", 60, 1)).isTrue();
            assertThat(nonces.tryConsume("app", "n-paused", 60)).isFalse();
            assertThatThrownBy(session::requireAvailable).isInstanceOf(BusinessException.class);
            assertThat(locks.tryLock("sched:publish-scan")).isEqualTo(LockAcquire.DEGRADED);
            assertThat(locks.tryClaimLock(9L)).isEqualTo(LockAcquire.DEGRADED);
            assertThat(cache.get(CacheNamespace.DICT, "warm", CacheProbe.class, () -> new CacheProbe("reload")))
                    .isEqualTo(new CacheProbe("l1"));
            AtomicInteger loads = new AtomicInteger();
            assertThat(cache.get(CacheNamespace.DICT, "miss", CacheProbe.class, () -> {
                        loads.incrementAndGet();
                        return new CacheProbe("db");
                    }))
                    .isEqualTo(new CacheProbe("db"));
            assertThat(loads.get()).isEqualTo(1);
            relay.tick();
            assertThat(outbox.countPending(OutboxProducer.ADMIN.id())).isEqualTo(1);
            assertThat(consumed.get()).isZero();
        });

        drain.awaitDrain(Duration.ofSeconds(5));
        assertThat(outbox.countPending(OutboxProducer.ADMIN.id())).isZero();
        assertThat(consumed.get()).isEqualTo(1);
        session.requireAvailable();
        assertThat(locks.tryLock("sched:publish-scan")).isEqualTo(LockAcquire.ACQUIRED);
        locks.unlock("sched:publish-scan");
    }

    private record CacheProbe(String value) {}
}
