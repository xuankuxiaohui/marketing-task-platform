package com.mkt.reward.application;

import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.domain.RewardTargets;
import com.mkt.reward.domain.TypeParams;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fulfillment dispatch helpers extracted from {@link FulfillmentService} (R13-5).
 * Move-only: armThirdPartyStub / dispatchAfterCommit semantics unchanged.
 */
@Component
public class FulfillmentDispatch {

    private final GrantRecordStore grants;

    @Autowired
    public FulfillmentDispatch(GrantRecordStore grants) {
        this.grants = grants;
    }

    void armThirdPartyStub(GrantRecordEntity record, PrizeEntity prize, PrizeCategoryEntity category) {
        dispatchAfterCommit(record, prize, category);
    }

    private void dispatchAfterCommit(
            GrantRecordEntity record, PrizeEntity prize, PrizeCategoryEntity category) {
        if (prize == null || !RewardTargets.THIRD_PARTY.equals(prize.getRewardTarget())) {
            return;
        }
        if (record.getFulfillmentRef() != null && !record.getFulfillmentRef().isBlank()) {
            return;
        }
        String adapter = TypeParams.text(RewardJson.map(prize.getTypeParams()), TypeParams.ADAPTER_CODE);
        if (adapter == null && category != null) {
            adapter = category.getAdapterCode();
        }
        if (adapter == null || adapter.isBlank()) {
            adapter = "stub";
        }
        String ref = adapter + ":stub-" + record.getId();
        Runnable write = () -> grants.updateFulfillmentRef(record.getId(), ref);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }
}
