package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskListType;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.time.MutableClock;
import com.mkt.risk.domain.ListEntry;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskListKeys;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.testsupport.MemoryRiskListItemStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RiskListProjectionTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
    private final MemoryKeyValueStore redis = new MemoryKeyValueStore();
    private final MemoryRiskListItemStore db = new MemoryRiskListItemStore();
    private final RiskListProjection projection = new RiskListProjection(redis, db, clock);

    @Test
    void reconcileWritesPermanentAndExpiry() {
        insert("USER", "BLACK", "9", null);
        projection.reconcile(RiskDimension.USER, RiskListType.BLACK, "9");
        assertThat(redis.get(RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "9")))
                .isEqualTo(RiskListKeys.PERMANENT);

        Instant expire = clock.instant().plusSeconds(30);
        insert("IP", "BLACK", "8.8.8.8", LocalDateTime.ofInstant(expire, ZoneOffset.UTC));
        projection.reconcile(RiskDimension.IP, RiskListType.BLACK, "8.8.8.8");
        assertThat(redis.get(RiskListKeys.of(RiskDimension.IP, RiskListType.BLACK, "8.8.8.8")))
                .isEqualTo(String.valueOf(expire.toEpochMilli()));
    }

    @Test
    void lookupBackfillsFromDbAndDropsExpired() {
        insert("USER", "BLACK", "3", LocalDateTime.ofInstant(clock.instant().plusSeconds(10), ZoneOffset.UTC));
        ListEntry hit = projection.lookup(RiskDimension.USER, RiskListType.BLACK, "3");
        assertThat(hit).isNotNull();
        assertThat(hit.listValue()).isEqualTo("3");

        clock.setInstant(clock.instant().plusSeconds(20));
        assertThat(projection.lookup(RiskDimension.USER, RiskListType.BLACK, "3")).isNull();
        assertThat(redis.get(RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "3"))).isNull();
    }

    @Test
    void lookupUsesDbWhenRedisIsDown() {
        insert("USER", "BLACK", "7", null);
        redis.setAvailable(false);
        ListEntry hit = projection.lookup(RiskDimension.USER, RiskListType.BLACK, "7");
        assertThat(hit).isNotNull();
        assertThat(hit.listValue()).isEqualTo("7");
    }

    @Test
    void lookupManyMatchesSingleLookupForGhostAndHit() {
        insert("USER", "BLACK", "8", null);
        String ghost = RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "9");
        redis.set(ghost, RiskListKeys.PERMANENT);
        var keys = java.util.List.of(
                new RiskListProjection.LookupKey(RiskDimension.USER, RiskListType.BLACK, "8"),
                new RiskListProjection.LookupKey(RiskDimension.USER, RiskListType.BLACK, "9"));
        assertThat(projection.lookupMany(keys)).hasSize(1).first().extracting(ListEntry::listValue).isEqualTo("8");
        assertThat(redis.get(ghost)).isNull();
        assertThat(redis.get(RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "8")))
                .isEqualTo(RiskListKeys.PERMANENT);
    }

    private void insert(String dim, String type, String value, LocalDateTime expireAt) {
        RiskListItemEntity e = new RiskListItemEntity();
        e.setDimension(dim);
        e.setListType(type);
        e.setListValue(value);
        e.setReason("t");
        e.setDenyLogin(0);
        e.setEffectiveAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        e.setExpireAt(expireAt);
        e.setOperatorId(1L);
        e.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        db.insert(e);
    }
}
