package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.identity.command.CacheEvictCommand;
import com.mkt.identity.support.SystemErrorCodes;
import com.mkt.infra.cache.CacheErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CacheAdminAppServiceTest {

    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private PlatformCache cache;
    private CacheAdminAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache = new TwoLevelPlatformCache(new MemoryKeyValueStore());
        service = new CacheAdminAppService(
                cache, new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void statsListsClosedNamespacesAndSessionIsNa() {
        var page = service.stats();
        assertThat(page.total()).isEqualTo(CacheNamespace.values().length);
        assertThat(page.records())
                .anySatisfy(row -> {
                    assertThat(row.namespace()).isEqualTo("identity:session");
                    assertThat(row.keyCount()).isEqualTo("N/A");
                })
                .anySatisfy(row -> {
                    assertThat(row.namespace()).isEqualTo("ad:position");
                    assertThat(row.keyCount()).isEqualTo("0");
                });
    }

    @Test
    void evictRejectsSessionAndUnknownAndBadCombo() {
        assertThatThrownBy(
                        () -> service.evict(new CacheEvictCommand("NAMESPACE", "identity:session", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CacheErrorCodes.SESSION_FORBIDDEN);
        assertThatThrownBy(() -> service.evict(new CacheEvictCommand("KEY", "dict", null, "identity:session:x")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CacheErrorCodes.SESSION_FORBIDDEN);
        assertThatThrownBy(() -> service.evict(new CacheEvictCommand("NAMESPACE", "nope", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SystemErrorCodes.CACHE_NAMESPACE_UNKNOWN);
        assertThatThrownBy(() -> service.evict(new CacheEvictCommand("KEY", "dict", "p", "k")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void evictKeyAndAdPositionNoop() {
        cache.put(CacheNamespace.DICT, "province", "GD");
        var evicted = service.evict(new CacheEvictCommand("KEY", "dict", null, "province"));
        assertThat(evicted.evictedRedis()).isEqualTo(1);
        assertThat(cache.get(CacheNamespace.DICT, "province", String.class, () -> "db")).isEqualTo("db");

        var ad = service.evict(new CacheEvictCommand("NAMESPACE", "ad:position", null, null));
        assertThat(ad.evictedRedis()).isZero();
        assertThat(ad.notifiedInstances()).isZero();
        assertThat(outbox.all()).isNotEmpty();
    }
}
