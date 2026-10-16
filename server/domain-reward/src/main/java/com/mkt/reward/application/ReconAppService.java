package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.command.ReconReviewCommand;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconGates;
import com.mkt.reward.domain.ReconReviewStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.ReconBatchEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.response.ReconActionResponse;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.response.ReconItemView;
import com.mkt.reward.response.ReconMatchResponse;
import com.mkt.reward.response.ReconReviewResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardOperator;
import com.mkt.reward.support.RewardRuntimeSettings;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Recon batches, match, review, and gated actions (design §5.11). */
@Service
public class ReconAppService {

    private final ReconItemStore items;
    private final GrantRecordStore grants;
    private final FulfillmentService fulfillment;
    private final Clock clock;
    private final ReconActionOps actionOps;
    private final ReconMatch reconMatch;
    private final ReconReads reconReads;
    private final ReconBatchWrites reconBatchWrites;

    @Autowired
    public ReconAppService(
            ReconBatchStore batches,
            ReconItemStore items,
            GrantRecordStore grants,
            PrizeCategoryStore categories,
            FulfillmentService fulfillment,
            Clock clock,
            ReconViews views,
            ReconActionOps actionOps,
            ReconMatch reconMatch,
            ReconReads reconReads,
            ReconBatchWrites reconBatchWrites) {
        this.items = items;
        this.grants = grants;
        this.fulfillment = fulfillment;
        this.clock = clock;
        this.actionOps = actionOps;
        this.reconMatch = reconMatch;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.reconReads = reconReads != null ? reconReads : new ReconReads(batches, items, views);
        this.reconBatchWrites = reconBatchWrites != null
                ? reconBatchWrites
                : new ReconBatchWrites(batches, items, categories, views, clock);
    }

    public ReconAppService(
            ReconBatchStore batches,
            ReconItemStore items,
            GrantRecordStore grants,
            PrizeStore prizes,
            PrizeCategoryStore categories,
            GrantAppService grantApp,
            FulfillmentService fulfillment,
            RewardRuntimeSettings settings,
            Clock clock) {
        this(
                batches,
                items,
                grants,
                categories,
                fulfillment,
                clock,
                new ReconViews(batches, grants, prizes, categories),
                new ReconActionOps(grants, prizes, grantApp, clock),
                new ReconMatch(
                        batches,
                        items,
                        grants,
                        fulfillment,
                        clock,
                        new ReconMatchSupport(items, grants, prizes, categories, settings, clock)),
                null,
                null);
    }

    public PageData<ReconBatchResponse> page(String categoryCode, LocalDate billDate, String status, PageQuery page) {
        return reconReads.page(categoryCode, billDate, status, page);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconBatchResponse create(ReconBatchCreateCommand command) {
        return reconBatchWrites.create(command);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void importLines(long batchId, ReconImportCommand command) {
        reconBatchWrites.importLines(batchId, command);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconMatchResponse match(long batchId) {
        return reconMatch.match(this, batchId);
    }

    public PageData<ReconItemView> items(long batchId, String result, String reviewStatus, PageQuery page) {
        return reconReads.items(batchId, result, reviewStatus, page);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconReviewResponse review(long itemId, ReconReviewCommand command) {
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconActionResponse action(long itemId, ReconActionCommand command) {
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

    ReconBatchEntity requireDraft(long batchId) {
        return reconBatchWrites.requireDraft(batchId);
    }

    private ReconItemEntity requireItem(long itemId) {
        ReconItemEntity item = items.getById(itemId);
        if (item == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return item;
    }
}
