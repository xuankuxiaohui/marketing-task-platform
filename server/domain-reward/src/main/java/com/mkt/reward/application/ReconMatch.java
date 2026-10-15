package com.mkt.reward.application;

import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ClaimWindows;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.ReconBatchStatuses;
import com.mkt.reward.domain.ReconResults;
import com.mkt.reward.domain.ReconReviewStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.ReconBatchEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.response.ReconMatchResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Match extracted from {@link ReconAppService} (R16-1).
 * Move-only: match semantics unchanged.
 * {@code match} takes the service so {@code requireDraft} stays a direct call.
 */
@Component
public class ReconMatch {

    private final ReconBatchStore batches;
    private final ReconItemStore items;
    private final GrantRecordStore grants;
    private final FulfillmentService fulfillment;
    private final Clock clock;
    private final ReconMatchSupport matchSupport;

    @Autowired
    public ReconMatch(
            ReconBatchStore batches,
            ReconItemStore items,
            GrantRecordStore grants,
            FulfillmentService fulfillment,
            Clock clock,
            ReconMatchSupport matchSupport) {
        this.batches = batches;
        this.items = items;
        this.grants = grants;
        this.fulfillment = fulfillment;
        this.clock = clock;
        this.matchSupport = matchSupport;
    }

    ReconMatchResponse match(ReconAppService service, long batchId) {
        ReconBatchEntity batch = service.requireDraft(batchId);
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
