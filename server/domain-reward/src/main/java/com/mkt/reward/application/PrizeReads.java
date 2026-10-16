package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.query.PrizeQuery;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.response.StockLogView;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read entries extracted from {@link PrizeAppService} (R33-4).
 * Move-only: get / page / stockLogs / require semantics unchanged.
 * {@code require} is package-visible so facade {@code delete} can delegate.
 */
@Component
public class PrizeReads {

    private final PrizeStore prizes;
    private final StockLogStore stockLogs;
    private final PrizeViews views;

    @Autowired
    public PrizeReads(PrizeStore prizes, StockLogStore stockLogs, PrizeViews views) {
        this.prizes = prizes;
        this.stockLogs = stockLogs;
        this.views = views;
    }

    PrizeResponse get(long id) {
        return views.toResponse(require(id));
    }

    PageData<PrizeResponse> page(PrizeQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = prizes.count(query.code(), query.name(), query.categoryCode(), query.status(), query.groupId());
        List<PrizeEntity> rows = prizes.list(
                query.code(),
                query.name(),
                query.categoryCode(),
                query.status(),
                query.groupId(),
                page.offset(),
                page.pageSize());
        return new PageData<>(total, rows.stream().map(views::toResponse).toList());
    }

    PageData<StockLogView> stockLogs(long id, PageQuery page) {
        require(id);
        long total = stockLogs.countByPrize(id);
        List<StockLogEntity> rows = stockLogs.listByPrize(id, page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(views::toLogView).toList());
    }

    PrizeEntity require(long id) {
        PrizeEntity existing = prizes.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }
}
