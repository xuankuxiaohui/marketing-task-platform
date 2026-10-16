package com.mkt.reward.application;

import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.command.ReconReviewCommand;
import com.mkt.reward.entity.ReconBatchEntity;
import com.mkt.reward.response.ReconActionResponse;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.response.ReconItemView;
import com.mkt.reward.response.ReconMatchResponse;
import com.mkt.reward.response.ReconReviewResponse;
import com.mkt.reward.support.RewardRuntimeSettings;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Recon batches, match, review, and gated actions (design §5.11). */
@Service
public class ReconAppService {

    private final ReconMatch reconMatch;
    private final ReconReads reconReads;
    private final ReconBatchWrites reconBatchWrites;
    private final ReconItemWrites reconItemWrites;

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
            ReconBatchWrites reconBatchWrites,
            ReconItemWrites reconItemWrites) {
        this.reconMatch = reconMatch;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.reconReads = reconReads != null ? reconReads : new ReconReads(batches, items, views);
        this.reconBatchWrites = reconBatchWrites != null
                ? reconBatchWrites
                : new ReconBatchWrites(batches, items, categories, views, clock);
        this.reconItemWrites = reconItemWrites != null
                ? reconItemWrites
                : new ReconItemWrites(items, grants, fulfillment, clock, actionOps);
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
        return reconItemWrites.review(itemId, command);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReconActionResponse action(long itemId, ReconActionCommand command) {
        return reconItemWrites.action(itemId, command);
    }

    ReconBatchEntity requireDraft(long batchId) {
        return reconBatchWrites.requireDraft(batchId);
    }
}
