package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.entity.ReconBatchEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.response.ReconItemView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read entries extracted from {@link ReconAppService} (R31-1).
 * Move-only: page / items semantics unchanged.
 */
@Component
public class ReconReads {

    private final ReconBatchStore batches;
    private final ReconItemStore items;
    private final ReconViews views;

    @Autowired
    public ReconReads(ReconBatchStore batches, ReconItemStore items, ReconViews views) {
        this.batches = batches;
        this.items = items;
        this.views = views;
    }

    PageData<ReconBatchResponse> page(String categoryCode, LocalDate billDate, String status, PageQuery page) {
        PageQuery pager = page == null ? PageQuery.of(null, null) : page;
        long total = batches.count(categoryCode, billDate, status);
        List<ReconBatchResponse> views = new ArrayList<>();
        for (ReconBatchEntity row : batches.list(categoryCode, billDate, status, pager.offset(), pager.pageSize())) {
            views.add(this.views.toBatch(row));
        }
        return new PageData<>(total, views);
    }

    PageData<ReconItemView> items(long batchId, String result, String reviewStatus, PageQuery page) {
        requireBatch(batchId);
        PageQuery pager = page == null ? PageQuery.of(null, null) : page;
        long total = items.count(batchId, result, reviewStatus);
        List<ReconItemView> views = new ArrayList<>();
        for (ReconItemEntity row : items.list(batchId, result, reviewStatus, pager.offset(), pager.pageSize())) {
            views.add(this.views.toItem(row));
        }
        return new PageData<>(total, views);
    }

    private ReconBatchEntity requireBatch(long batchId) {
        ReconBatchEntity batch = batches.getById(batchId);
        if (batch == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return batch;
    }
}
