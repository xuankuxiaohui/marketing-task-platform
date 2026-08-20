package com.mkt.reward.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.PrizeEntity;
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
}
