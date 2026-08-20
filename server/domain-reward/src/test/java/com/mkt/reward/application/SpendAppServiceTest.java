package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.testsupport.MemoryGrantRecordStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SpendAppServiceTest {

    @Test
    void simulatedExcludedAndSendingIsInFlight() {
        MemoryGrantRecordStore grants = new MemoryGrantRecordStore();
        grants.insert(row(1L, GrantRecordStatuses.FULFILL_ARRIVED, 10, 0));
        grants.insert(row(2L, GrantRecordStatuses.FULFILL_SENDING, 7, 0));
        grants.insert(row(3L, GrantRecordStatuses.FULFILL_ARRIVED, 99, 1));
        SpendAppService spend = new SpendAppService(grants);
        var response = spend.spend("ALIPAY_RED", null, null, null);
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().getFirst().arrivedCount()).isEqualTo(1);
        assertThat(response.rows().getFirst().arrivedCostFen()).isEqualTo(10);
        assertThat(response.rows().getFirst().sendingCount()).isEqualTo(1);
        assertThat(response.rows().getFirst().sendingCostFen()).isEqualTo(7);
    }

    private static GrantRecordEntity row(long id, String fulfillment, int cost, int simulated) {
        GrantRecordEntity entity = new GrantRecordEntity();
        entity.setId(id);
        entity.setPrizeId(1L);
        entity.setPrizeCode("p");
        entity.setCategoryCode("ALIPAY_RED");
        entity.setCostFen(cost);
        entity.setReconStatus(GrantRecordStatuses.RECON_NONE);
        entity.setUserId(9L);
        entity.setGrantSource("TASK_STEP");
        entity.setSourceId("s-" + id);
        entity.setStatus(GrantRecordStatuses.GRANTED);
        entity.setFulfillmentStatus(fulfillment);
        entity.setRetryCount(0);
        entity.setSimulated(simulated);
        entity.setGrantedAt(LocalDateTime.parse("2026-08-19T00:00:00"));
        entity.setCreatedAt(entity.getGrantedAt());
        entity.setUpdatedAt(entity.getGrantedAt());
        return entity;
    }
}
