package com.mkt.reward.application;

import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ReconPolicies;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.ReconBatchEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.response.ReconItemView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * View mapping helpers extracted from {@link ReconAppService} (R7-1).
 * Move-only: toBatch / toItem / nz semantics unchanged.
 */
@Component
public class ReconViews {

    private final ReconBatchStore batches;
    private final GrantRecordStore grants;
    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;

    @Autowired
    public ReconViews(
            ReconBatchStore batches, GrantRecordStore grants, PrizeStore prizes, PrizeCategoryStore categories) {
        this.batches = batches;
        this.grants = grants;
        this.prizes = prizes;
        this.categories = categories;
    }

    ReconItemView toItem(ReconItemEntity row) {
        GrantRecordEntity grant = row.getGrantRecordId() == null ? null : grants.getById(row.getGrantRecordId());
        String failReason = grant == null ? null : grant.getFulfillFailReason();
        String policy;
        if (grant != null) {
            policy = effectivePolicy(grant);
        } else {
            ReconBatchEntity batch = batches.getById(row.getBatchId());
            PrizeCategoryEntity category = batch == null ? null : categories.getByCode(batch.getCategoryCode());
            policy = category == null ? ReconPolicies.REVIEW : category.getReconActionPolicy();
        }
        return new ReconItemView(
                row.getId(),
                row.getBatchId(),
                row.getGrantRecordId(),
                row.getFulfillmentRef(),
                row.getPlatformCostFen(),
                row.getChannelAmountFen(),
                row.getResult(),
                row.getAction(),
                row.getActionRef(),
                row.getReviewStatus(),
                failReason,
                policy,
                row.getRemark());
    }

    ReconBatchResponse toBatch(ReconBatchEntity row) {
        return new ReconBatchResponse(
                row.getId(),
                row.getCategoryCode(),
                row.getBillDate(),
                row.getStatus(),
                nz(row.getPlatformCount()),
                nz(row.getChannelCount()),
                nz(row.getMatchedCount()),
                nz(row.getPlatformOnly()),
                nz(row.getChannelOnly()),
                nz(row.getAmountMismatch()),
                RewardTime.toInstant(row.getCreatedAt()));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String effectivePolicy(GrantRecordEntity grant) {
        PrizeEntity prize = prizes.getByIdIncludingDeleted(grant.getPrizeId());
        PrizeCategoryEntity category = prize == null ? null : categories.getByCode(prize.getCategoryCode());
        String prizePolicy = prize == null ? null : prize.getReconActionPolicy();
        String categoryPolicy = category == null ? ReconPolicies.REVIEW : category.getReconActionPolicy();
        return ReconPolicies.effective(prizePolicy, categoryPolicy);
    }
}
