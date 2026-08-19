package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.time.MutableClock;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.testsupport.MemoryRiskListItemStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ListLookupTest {

    @Test
    void decideUsesProjectionSnapshot() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        MutableClock clock = new MutableClock(now);
        MemoryRiskListItemStore db = new MemoryRiskListItemStore();
        RiskListItemEntity e = new RiskListItemEntity();
        e.setDimension("USER");
        e.setListType("WHITE");
        e.setListValue("4");
        e.setReason("vip");
        e.setDenyLogin(0);
        e.setEffectiveAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        e.setOperatorId(1L);
        e.setCreatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        db.insert(e);
        ListLookup lookup = new ListLookup(new RiskListProjection(new MemoryKeyValueStore(), db, clock), clock);
        assertThat(lookup.find(RiskDimension.USER, RiskListType.WHITE, "4")).isNotNull();
        assertThat(lookup.decide(RiskScene.CLAIM, new RiskSubject(4L, "2.2.2.2", "d", null)))
                .isEqualTo(ListDecision.SKIP_RULES);
    }
}
