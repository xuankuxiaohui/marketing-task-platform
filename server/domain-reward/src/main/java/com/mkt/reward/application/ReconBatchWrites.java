package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.command.ReconImportLineCommand;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconBatchStatuses;
import com.mkt.reward.domain.ReconResults;
import com.mkt.reward.domain.ReconReviewStatuses;
import com.mkt.reward.entity.ReconBatchEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardOperator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Batch write entries extracted from {@link ReconAppService} (R31-2).
 * Move-only: create / importLines semantics unchanged.
 */
@Component
public class ReconBatchWrites {

    private final ReconBatchStore batches;
    private final ReconItemStore items;
    private final PrizeCategoryStore categories;
    private final ReconViews views;
    private final Clock clock;

    @Autowired
    public ReconBatchWrites(
            ReconBatchStore batches,
            ReconItemStore items,
            PrizeCategoryStore categories,
            ReconViews views,
            Clock clock) {
        this.batches = batches;
        this.items = items;
        this.categories = categories;
        this.views = views;
        this.clock = clock;
    }

    ReconBatchResponse create(ReconBatchCreateCommand command) {
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

    void importLines(long batchId, ReconImportCommand command) {
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

    ReconBatchEntity requireDraft(long batchId) {
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
}
