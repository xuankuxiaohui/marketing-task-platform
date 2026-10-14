package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.command.ReconImportLineCommand;
import com.mkt.reward.command.ReconReviewCommand;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ClaimWindows;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconBatchStatuses;
import com.mkt.reward.domain.ReconGates;
import com.mkt.reward.domain.ReconResults;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Recon batches, match, review, and gated actions (design §5.11). */
@Service
public class ReconAppService {

    private final ReconBatchStore batches;
    private final ReconItemStore items;
    private final GrantRecordStore grants;
    private final PrizeCategoryStore categories;
    private final FulfillmentService fulfillment;
    private final Clock clock;
    private final ReconViews views;
    private final ReconMatchSupport matchSupport;
    private final ReconActionOps actionOps;

    @Autowired
    public ReconAppService(
            ReconBatchStore batches,
            ReconItemStore items,
            GrantRecordStore grants,
            PrizeCategoryStore categories,
            FulfillmentService fulfillment,
            Clock clock,
            ReconViews views,
            ReconMatchSupport matchSupport,
            ReconActionOps actionOps) {
        this.batches = batches;
        this.items = items;
        this.grants = grants;
        this.categories = categories;
        this.fulfillment = fulfillment;
        this.clock = clock;
        this.views = views;
        this.matchSupport = matchSupport;
        this.actionOps = actionOps;
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
                new ReconMatchSupport(items, grants, prizes, categories, settings, clock),
                new ReconActionOps(grants, prizes, grantApp, clock));
    }

    public PageData<ReconBatchResponse> page(String categoryCode, LocalDate billDate, String status, PageQuery page) {
        PageQuery pager = page == null ? PageQuery.of(null, null) : page;
        long total = batches.count(categoryCode, billDate, status);
        List<ReconBatchResponse> views = new ArrayList<>();
        for (ReconBatchEntity row : batches.list(categoryCode, billDate, status, pager.offset(), pager.pageSize())) {
            views.add(this.views.toBatch(row));
        }
        return new PageData<>(total, views);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconBatchResponse create(ReconBatchCreateCommand command) {
        if (categories.getByCode(command.categoryCode()) == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        if (batches.getByCategoryAndDay(command.categoryCode(), command.billDate()) != null) {
            throw new BusinessException(RewardErrorCodes.RECON_DUPLICATE_DAY);
        }
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        ReconBatchEntity entity = new ReconBatchEntity();
        entity.setCategoryCode(command.categoryCode());
        entity.setBillDate(command.billDate());
        entity.setStatus(ReconBatchStatuses.DRAFT);
        entity.setPlatformCount(0);
        entity.setChannelCount(0);
        entity.setMatchedCount(0);
        entity.setPlatformOnly(0);
        entity.setChannelOnly(0);
        entity.setAmountMismatch(0);
        entity.setOperatorId(RewardOperator.requireUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            batches.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(RewardErrorCodes.RECON_DUPLICATE_DAY);
        }
        return views.toBatch(entity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void importLines(long batchId, ReconImportCommand command) {
        ReconBatchEntity batch = requireDraft(batchId);
        Set<String> seen = new HashSet<>();
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        for (ReconImportLineCommand line : command.lines()) {
            String ref = line.fulfillmentRef() == null ? "" : line.fulfillmentRef().trim();
            if (ref.isEmpty() || line.amountFen() == null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
            }
            if (!seen.add(ref)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "渠道账单 fulfillmentRef 重复");
            }
            ReconItemEntity item = new ReconItemEntity();
            item.setBatchId(batch.getId());
            item.setFulfillmentRef(ref);
            item.setChannelAmountFen(line.amountFen());
            item.setResult(ReconResults.CHANNEL_ONLY);
            item.setAction(ReconActions.NONE);
            item.setReviewStatus(ReconReviewStatuses.NONE);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            try {
                items.insert(item);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "渠道账单 fulfillmentRef 重复");
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconMatchResponse match(long batchId) {
        ReconBatchEntity batch = requireDraft(batchId);
        batch.setStatus(ReconBatchStatuses.MATCHING);
        batches.update(batch);
        LocalDateTime from = ClaimWindows.businessDayStartUtc(batch.getBillDate());
        LocalDateTime to = ClaimWindows.businessDayStartUtc(batch.getBillDate().plusDays(1));
        List<GrantRecordEntity> platform = grants.listPlatformRecon(batch.getCategoryCode(), from, to);
        List<ReconItemEntity> channel = items.listAll(batch.getId());
        Map<String, ReconItemEntity> byRef = new HashMap<>();
        for (ReconItemEntity item : channel) {
            if (item.getFulfillmentRef() != null) {
                byRef.put(item.getFulfillmentRef(), item);
            }
        }
        int matched = 0;
        int mismatch = 0;
        int platformOnly = 0;
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        for (GrantRecordEntity grant : platform) {
            String ref = grant.getFulfillmentRef();
            ReconItemEntity hit = ref == null ? null : byRef.remove(ref);
            if (hit == null) {
                matchSupport.insertPlatformOnly(batch.getId(), grant, now);
                platformOnly++;
                continue;
            }
            hit.setGrantRecordId(grant.getId());
            hit.setPlatformCostFen(grant.getCostFen());
            int channelFen = hit.getChannelAmountFen() == null ? 0 : hit.getChannelAmountFen();
            int platformFen = grant.getCostFen() == null ? 0 : grant.getCostFen();
            if (platformFen == channelFen) {
                hit.setResult(ReconResults.MATCHED);
                hit.setReviewStatus(ReconReviewStatuses.NONE);
                grant.setReconStatus(GrantRecordStatuses.RECON_MATCHED);
                matched++;
            } else {
                hit.setResult(ReconResults.AMOUNT_MISMATCH);
                hit.setReviewStatus(ReconReviewStatuses.NONE);
                grant.setReconStatus(GrantRecordStatuses.RECON_DIFF);
                mismatch++;
            }
            hit.setUpdatedAt(now);
            items.update(hit);
            grants.update(grant);
        }
        int channelOnly = byRef.size();
        for (ReconItemEntity leftover : byRef.values()) {
            leftover.setResult(ReconResults.CHANNEL_ONLY);
            leftover.setReviewStatus(ReconReviewStatuses.NONE);
            leftover.setUpdatedAt(now);
            items.update(leftover);
        }
        matchSupport.applyReviewFlags(batch.getId());
        batch.setPlatformCount(platform.size());
        batch.setChannelCount(channel.size());
        batch.setMatchedCount(matched);
        batch.setPlatformOnly(platformOnly);
        batch.setChannelOnly(channelOnly);
        batch.setAmountMismatch(mismatch);
        batch.setStatus(ReconBatchStatuses.DONE);
        batch.setUpdatedAt(now);
        batches.update(batch);
        List<Long> autoIds = matchSupport.autoRefulfillIds(batch.getId());
        afterCommit(() -> {
            for (Long id : autoIds) {
                try {
                    fulfillment.retry(id);
                } catch (RuntimeException ignored) {
                    // match already DONE; auto REFULFILL is best-effort
                }
            }
        });
        return new ReconMatchResponse(
                platform.size(), channel.size(), matched, platformOnly, channelOnly, mismatch);
    }

    public PageData<ReconItemView> items(long batchId, String result, String reviewStatus, PageQuery page) {
        requireBatch(batchId);
        PageQuery pager = page == null ? PageQuery.of(null, null) : page;
        long total = items.count(batchId, result, reviewStatus);
        List<ReconItemView> views = new ArrayList<>();
        for (ReconItemEntity row : items.list(batchId, result, reviewStatus, pager.offset(), pager.pageSize())) {
            views.add(this.views.toItem(row));
        }
        return new PageData<>(total, views);
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

    private ReconBatchEntity requireDraft(long batchId) {
        ReconBatchEntity batch = requireBatch(batchId);
        if (!ReconBatchStatuses.DRAFT.equals(batch.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return batch;
    }

    private ReconBatchEntity requireBatch(long batchId) {
        ReconBatchEntity batch = batches.getById(batchId);
        if (batch == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return batch;
    }

    private ReconItemEntity requireItem(long itemId) {
        ReconItemEntity item = items.getById(itemId);
        if (item == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return item;
    }

    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
