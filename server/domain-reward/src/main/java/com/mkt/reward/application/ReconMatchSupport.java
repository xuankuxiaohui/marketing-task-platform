package com.mkt.reward.application;

import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconGates;
import com.mkt.reward.domain.ReconPolicies;
import com.mkt.reward.domain.ReconResults;
import com.mkt.reward.domain.ReconReviewStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.support.RewardRuntimeSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Match-support helpers extracted from {@link ReconAppService} (R7-2).
 * Move-only: insertPlatformOnly / applyReviewFlags / autoRefulfillIds semantics unchanged.
 */
@Component
public class ReconMatchSupport {

    private final ReconItemStore items;
    private final GrantRecordStore grants;
    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;
    private final RewardRuntimeSettings settings;
    private final Clock clock;

    @Autowired
    public ReconMatchSupport(
            ReconItemStore items,
            GrantRecordStore grants,
            PrizeStore prizes,
            PrizeCategoryStore categories,
            RewardRuntimeSettings settings,
            Clock clock) {
        this.items = items;
        this.grants = grants;
        this.prizes = prizes;
        this.categories = categories;
        this.settings = settings;
        this.clock = clock;
    }

    void insertPlatformOnly(long batchId, GrantRecordEntity grant, LocalDateTime now) {
        ReconItemEntity item = new ReconItemEntity();
        item.setBatchId(batchId);
        item.setGrantRecordId(grant.getId());
        item.setFulfillmentRef(grant.getFulfillmentRef());
        item.setPlatformCostFen(grant.getCostFen());
        item.setResult(ReconResults.PLATFORM_ONLY);
        item.setAction(ReconActions.NONE);
        item.setReviewStatus(ReconReviewStatuses.NONE);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        items.insert(item);
        grant.setReconStatus(GrantRecordStatuses.RECON_DIFF);
        grants.update(grant);
    }

    void applyReviewFlags(long batchId) {
        for (ReconItemEntity item : items.listAll(batchId)) {
            GrantRecordEntity grant = item.getGrantRecordId() == null ? null : grants.getById(item.getGrantRecordId());
            String fulfillment = grant == null ? null : grant.getFulfillmentStatus();
            String failReason = grant == null ? null : grant.getFulfillFailReason();
            if (ReconGates.needsChannelReview(item.getResult(), fulfillment, failReason)) {
                item.setReviewStatus(ReconReviewStatuses.PENDING_REVIEW);
                items.update(item);
            }
        }
    }

    List<Long> autoRefulfillIds(long batchId) {
        List<Long> ids = new ArrayList<>();
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        for (ReconItemEntity item : items.listAll(batchId)) {
            GrantRecordEntity grant = item.getGrantRecordId() == null ? null : grants.getById(item.getGrantRecordId());
            if (grant == null) {
                continue;
            }
            String policy = effectivePolicy(grant);
            if (!ReconGates.autoRefulfillEligible(
                    settings.autoRefulfillEnabled(),
                    policy,
                    item.getResult(),
                    grant.getFulfillmentStatus(),
                    grant.getFulfillFailReason())) {
                continue;
            }
            item.setAction(ReconActions.REFULFILL);
            item.setActionRef(String.valueOf(grant.getId()));
            item.setUpdatedAt(now);
            if (items.casAction(item) == 1) {
                ids.add(grant.getId());
            }
        }
        return ids;
    }

    private String effectivePolicy(GrantRecordEntity grant) {
        PrizeEntity prize = prizes.getByIdIncludingDeleted(grant.getPrizeId());
        PrizeCategoryEntity category = prize == null ? null : categories.getByCode(prize.getCategoryCode());
        String prizePolicy = prize == null ? null : prize.getReconActionPolicy();
        String categoryPolicy = category == null ? ReconPolicies.REVIEW : category.getReconActionPolicy();
        return ReconPolicies.effective(prizePolicy, categoryPolicy);
    }
}
