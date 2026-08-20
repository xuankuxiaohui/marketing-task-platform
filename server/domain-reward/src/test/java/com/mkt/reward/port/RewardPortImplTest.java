package com.mkt.reward.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.testsupport.MemoryGrantRecordStore;
import com.mkt.reward.testsupport.MemoryPointsStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import org.junit.jupiter.api.Test;

class RewardPortImplTest {

    @Test
    void prizeEnabledRequiresEnabledAndNotDeleted() {
        MemoryPrizeStore store = new MemoryPrizeStore();
        PrizeEntity enabled = new PrizeEntity();
        enabled.setStatus(PrizeStatuses.ENABLED);
        enabled.setDeleted(0);
        store.insert(enabled);
        PrizeEntity draft = new PrizeEntity();
        draft.setStatus(PrizeStatuses.DRAFT);
        draft.setDeleted(0);
        store.insert(draft);
        RewardPortImpl port = new RewardPortImpl(null, store);
        assertThat(port.prizeEnabled(enabled.getId())).isTrue();
        assertThat(port.prizeEnabled(draft.getId())).isFalse();
        assertThat(port.prizeEnabled(99L)).isFalse();
        assertThat(port.consume(1L, 0, "SIGNIN", "x", "noop")).isZero();
        assertThatThrownBy(() -> port.consume(1L, 10, "SIGNIN", "c", "catchup"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void grantDelegates() {
        MemoryPrizeStore store = new MemoryPrizeStore();
        GrantAppService grants = mock(GrantAppService.class);
        GrantContext ctx = GrantContext.defaults();
        when(grants.grant(1L, 2L, GrantSource.TASK_STEP, "s", ctx))
                .thenReturn(new GrantResult(9L, GrantStatus.GRANTED, FulfillmentStatus.ARRIVED, 1L, false));
        RewardPortImpl port = new RewardPortImpl(grants, store);
        GrantResult result = port.grant(1L, 2L, GrantSource.TASK_STEP, "s", ctx);
        assertThat(result.recordId()).isEqualTo(9L);
        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
    }

    @Test
    void userSummaryReadsBalanceAndPrizeCounts() {
        MemoryPrizeStore prizes = new MemoryPrizeStore();
        MemoryGrantRecordStore records = new MemoryGrantRecordStore();
        GrantRecordEntity won = new GrantRecordEntity();
        won.setUserId(5L);
        won.setGrantSource("TASK_STEP");
        won.setSourceId("a");
        won.setPrizeId(1L);
        won.setStatus(GrantRecordStatuses.WON);
        won.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        records.insert(won);
        GrantRecordEntity granted = new GrantRecordEntity();
        granted.setUserId(5L);
        granted.setGrantSource("TASK_STEP");
        granted.setSourceId("b");
        granted.setPrizeId(2L);
        granted.setStatus(GrantRecordStatuses.GRANTED);
        granted.setFulfillmentStatus(GrantRecordStatuses.FULFILL_ARRIVED);
        records.insert(granted);
        GrantRecordEntity other = new GrantRecordEntity();
        other.setUserId(6L);
        other.setGrantSource("TASK_STEP");
        other.setSourceId("c");
        other.setPrizeId(3L);
        other.setStatus(GrantRecordStatuses.WON);
        other.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        records.insert(other);
        MemoryPointsStore pointsStore = new MemoryPointsStore();
        PointsAppService points = new PointsAppService(
                pointsStore, (org.springframework.transaction.PlatformTransactionManager) null, java.time.Clock.systemUTC());
        points.earn(5L, 40, null, "TASK_STEP", "s");
        RewardPortImpl port = new RewardPortImpl(null, prizes, points, records);
        var summary = port.userSummary(5L);
        assertThat(summary.pointsBalance()).isEqualTo(40L);
        assertThat(summary.prizeSummary().won()).isEqualTo(1L);
        assertThat(summary.prizeSummary().granted()).isEqualTo(1L);
        assertThat(port.userSummary(99L).pointsBalance()).isZero();
        assertThat(port.consume(5L, 0, "SIGNIN", "x", "noop")).isEqualTo(40L);
        assertThat(port.consume(5L, 10, "SIGNIN", "c", "catchup")).isEqualTo(30L);
    }
}
