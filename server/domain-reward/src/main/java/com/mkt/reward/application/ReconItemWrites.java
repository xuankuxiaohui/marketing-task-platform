package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconReviewCommand;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconGates;
import com.mkt.reward.domain.ReconReviewStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.response.ReconActionResponse;
import com.mkt.reward.response.ReconReviewResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardOperator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Item write entries extracted from {@link ReconAppService} (R31-3).
 * Move-only: review / action semantics unchanged.
 */
@Component
public class ReconItemWrites {

    private final ReconItemStore items;
    private final GrantRecordStore grants;
    private final FulfillmentService fulfillment;
    private final Clock clock;
    private final ReconActionOps actionOps;

    @Autowired
    public ReconItemWrites(
            ReconItemStore items,
            GrantRecordStore grants,
            FulfillmentService fulfillment,
            Clock clock,
            ReconActionOps actionOps) {
        this.items = items;
        this.grants = grants;
        this.fulfillment = fulfillment;
        this.clock = clock;
        this.actionOps = actionOps;
    }

    ReconReviewResponse review(long itemId, ReconReviewCommand command) {
        ReconItemEntity item = requireItem(itemId);
        if (!ReconReviewStatuses.PENDING_REVIEW.equals(item.getReviewStatus())) {
            throw new BusinessException(RewardErrorCodes.RECON_NOT_PENDING_REVIEW);
        }
        String decision = command.decision() == null ? "" : command.decision().trim();
        if ("CONFIRM".equals(decision)) {
            item.setReviewStatus(ReconReviewStatuses.CONFIRMED);
        } else if ("REJECT".equals(decision)) {
            item.setReviewStatus(ReconReviewStatuses.REJECTED);
        } else {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        item.setReviewerId(RewardOperator.requireUserId());
        item.setReviewedAt(now);
        item.setReviewRemark(command.remark().trim());
        item.setUpdatedAt(now);
        int affected = items.casReview(item);
        if (affected != 1) {
            throw new BusinessException(RewardErrorCodes.RECON_NOT_PENDING_REVIEW);
        }
        return new ReconReviewResponse(item.getReviewStatus());
    }

    ReconActionResponse action(long itemId, ReconActionCommand command) {
        ReconItemEntity item = requireItem(itemId);
        if (!ReconActions.NONE.equals(item.getAction())) {
            throw new BusinessException(RewardErrorCodes.RECON_ACTION_DONE);
        }
        String action = command.action() == null ? "" : command.action().trim();
        GrantRecordEntity grant = item.getGrantRecordId() == null ? null : grants.getById(item.getGrantRecordId());
        String fulfillmentStatus = grant == null ? null : grant.getFulfillmentStatus();
        String failReason = grant == null ? null : grant.getFulfillFailReason();
        Set<String> allowed =
                ReconGates.allowedActions(item.getResult(), fulfillmentStatus, failReason, item.getReviewStatus());
        if (!allowed.contains(action)) {
            if (ReconGates.reviewRequiredFor(item.getResult(), fulfillmentStatus, failReason, action)
                    && ReconReviewStatuses.PENDING_REVIEW.equals(item.getReviewStatus())) {
                throw new BusinessException(RewardErrorCodes.RECON_REVIEW_REQUIRED);
            }
            throw new BusinessException(RewardErrorCodes.RECON_ACTION_FORBIDDEN);
        }
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        String actionRef = null;
        if (ReconActions.ABSORB.equals(action)) {
            item.setRemark(command.reason());
        } else if (ReconActions.REFULFILL.equals(action)) {
            if (grant == null) {
                throw new BusinessException(RewardErrorCodes.RECON_ACTION_FORBIDDEN);
            }
            this.fulfillment.retry(grant.getId());
            actionRef = String.valueOf(grant.getId());
        } else if (ReconActions.MANUAL_GRANT.equals(action)) {
            actionRef = actionOps.doManualGrant(item, grant, command);
        } else if (ReconActions.LEDGER_ONLY.equals(action)) {
            actionRef = actionOps.doLedgerOnly(item, command, now);
        } else {
            throw new BusinessException(RewardErrorCodes.RECON_ACTION_FORBIDDEN);
        }
        item.setAction(action);
        item.setActionRef(actionRef);
        item.setUpdatedAt(now);
        int affected = items.casAction(item);
        if (affected != 1) {
            throw new BusinessException(RewardErrorCodes.RECON_ACTION_DONE);
        }
        return new ReconActionResponse(action, actionRef);
    }

    private ReconItemEntity requireItem(long itemId) {
        ReconItemEntity item = items.getById(itemId);
        if (item == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return item;
    }
}
