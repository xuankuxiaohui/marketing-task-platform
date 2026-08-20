package com.mkt.reward.port;

import static org.assertj.core.api.Assertions.assertThat;

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
        RewardPortImpl port = new RewardPortImpl(store);
        assertThat(port.prizeEnabled(enabled.getId())).isTrue();
        assertThat(port.prizeEnabled(draft.getId())).isFalse();
        assertThat(port.prizeEnabled(99L)).isFalse();
    }
}
