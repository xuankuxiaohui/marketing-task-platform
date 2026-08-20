package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.testsupport.MemoryGrantRecordStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PrizePortalAppServiceTest {

    @Test
    void pendingTabHidesArrivedAndExpired() {
        MemoryGrantRecordStore grants = new MemoryGrantRecordStore();
        MemoryPrizeStore prizes = new MemoryPrizeStore();
        PrizeEntity prize = new PrizeEntity();
        prize.setId(1L);
        prize.setCode("p1");
        prize.setName("奖");
        prize.setCategoryCode("COUPON");
        prize.setRewardTarget("PLATFORM");
        prize.setFulfillmentMode("INSTANT");
        prize.setTotalStock(1);
        prize.setRemainingStock(0);
        prize.setDailyClaimLimit(0);
        prize.setTotalClaimLimit(0);
        prize.setClaimMode("MANUAL");
        prize.setStatus("ENABLED");
        prize.setDeleted(0);
        prizes.insert(prize);
        grants.insert(row(1L, GrantRecordStatuses.WON, GrantRecordStatuses.FULFILL_NONE));
        grants.insert(row(2L, GrantRecordStatuses.GRANTED, GrantRecordStatuses.FULFILL_ARRIVED));
        grants.insert(row(3L, GrantRecordStatuses.GRANTED, GrantRecordStatuses.FULFILL_SENDING));
        grants.insert(row(4L, GrantRecordStatuses.EXPIRED, GrantRecordStatuses.FULFILL_NONE));
        PrizePortalAppService portal = new PrizePortalAppService(grants, prizes);
        assertThat(portal.list(9L, "PENDING", 1, 20).records()).extracting(v -> v.recordId()).containsExactly(3L, 1L);
        assertThat(portal.list(9L, null, 1, 20).total()).isEqualTo(2);
        assertThat(portal.list(9L, "FOO", 1, 20).total()).isEqualTo(2);
        assertThat(portal.list(9L, "ALL", 1, 20).total()).isEqualTo(4);
    }

    private static GrantRecordEntity row(long id, String status, String fulfillment) {
        GrantRecordEntity entity = new GrantRecordEntity();
        entity.setId(id);
        entity.setPrizeId(1L);
        entity.setPrizeCode("p1");
        entity.setCategoryCode("COUPON");
        entity.setCostFen(0);
        entity.setReconStatus(GrantRecordStatuses.RECON_NONE);
        entity.setUserId(9L);
        entity.setGrantSource("TASK_STEP");
        entity.setSourceId("s-" + id);
        entity.setStatus(status);
        entity.setFulfillmentStatus(fulfillment);
        entity.setRetryCount(0);
        entity.setSimulated(0);
        entity.setCreatedAt(LocalDateTime.parse("2026-08-19T00:00:00"));
        entity.setUpdatedAt(entity.getCreatedAt());
        return entity;
    }
}
