package com.mkt.risk.convert;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.risk.entity.RiskHitLogEntity;
import com.mkt.risk.entity.RiskListItemEntity;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RiskConvertTest {

    @Test
    void listItemAndHitRoundTrip() {
        LocalDateTime now = LocalDateTime.ofInstant(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        RiskListItemEntity item = new RiskListItemEntity();
        item.setId(3L);
        item.setDimension("USER");
        item.setListType("BLACK");
        item.setListValue("9");
        item.setReason("r");
        item.setDenyLogin(1);
        item.setEffectiveAt(now);
        item.setExpireAt(now.plusHours(1));
        item.setOperatorId(2L);
        item.setRemark("m");
        item.setCreatedAt(now);
        var view = RiskListItemConvert.toResponse(item);
        assertThat(view.denyLogin()).isTrue();
        assertThat(view.listValue()).isEqualTo("9");
        assertThat(RiskListItemConvert.toEntry(item).denyLogin()).isTrue();

        RiskHitLogEntity hit = new RiskHitLogEntity();
        hit.setId(8L);
        hit.setHitType("LIST");
        hit.setRuleCode("USER:BLACK");
        hit.setUserId(9L);
        hit.setDimensionValue("9");
        hit.setContext("{}");
        hit.setHitValue("1");
        hit.setThreshold("1");
        hit.setActionResult("REJECTED");
        hit.setSimulated(0);
        hit.setOccurredAt(now);
        hit.setCreatedAt(now);
        assertThat(RiskHitLogConvert.toResponse(hit).hitType()).isEqualTo("LIST");
        assertThat(RiskTime.toUtc(view.effectiveAt())).isEqualTo(now);
    }
}
