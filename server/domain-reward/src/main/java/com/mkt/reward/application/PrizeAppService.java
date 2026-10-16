package com.mkt.reward.application;

import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.command.StockReplenishCommand;
import com.mkt.reward.query.PrizeQuery;
import com.mkt.reward.response.PrizeImpactResponse;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.response.StockLogView;
import com.mkt.reward.response.StockReplenishResponse;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrizeAppService {

    private final PrizeSave prizeSave;
    private final PrizeLifecycle prizeLifecycle;
    private final PrizeStock prizeStock;
    private final PrizeReads prizeReads;
    private final PrizeDelete prizeDelete;

    @Autowired
    public PrizeAppService(
            PrizeStore prizes,
            StockLogStore stockLogs,
            SnapshotPrizeLookup snapshots,
            Clock clock,
            PrizeValidate validate,
            PrizeApply apply,
            PrizeViews views,
            PrizeSave prizeSave,
            PrizeLifecycle prizeLifecycle,
            PrizeStock prizeStock,
            PrizeReads prizeReads,
            PrizeDelete prizeDelete) {
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.prizeSave = prizeSave != null ? prizeSave : new PrizeSave(prizes, clock, validate, apply, views);
        this.prizeLifecycle = prizeLifecycle != null
                ? prizeLifecycle
                : new PrizeLifecycle(prizes, snapshots, clock, validate);
        this.prizeStock = prizeStock != null ? prizeStock : new PrizeStock(prizes, stockLogs, clock);
        this.prizeReads = prizeReads != null ? prizeReads : new PrizeReads(prizes, stockLogs, views);
        this.prizeDelete = prizeDelete != null
                ? prizeDelete
                : new PrizeDelete(prizes, snapshots, clock, this.prizeReads);
    }

    public PrizeAppService(
            PrizeStore prizes,
            PrizeCategoryStore categories,
            StockLogStore stockLogs,
            SnapshotPrizeLookup snapshots,
            Clock clock) {
        this(
                prizes,
                stockLogs,
                snapshots,
                clock,
                new PrizeValidate(categories),
                new PrizeApply(),
                new PrizeViews(),
                null,
                null,
                null,
                null,
                null);
    }

    public PrizeResponse get(long id) {
        return prizeReads.get(id);
    }

    public PageData<PrizeResponse> page(PrizeQuery query) {
        return prizeReads.page(query);
    }

    @Transactional
    public PrizeResponse create(PrizeSaveCommand command) {
        return prizeSave.create(command);
    }

    @Transactional
    public PrizeResponse update(long id, PrizeSaveCommand command) {
        return prizeSave.update(id, command);
    }

    @Transactional
    public void delete(long id) {
        prizeDelete.delete(id);
    }

    @Transactional
    public PrizeImpactResponse disable(long id, PrizeConfirmCommand command) {
        return prizeLifecycle.disable(id, command);
    }

    @Transactional
    public PrizeImpactResponse enable(long id, PrizeConfirmCommand command) {
        return prizeLifecycle.enable(id, command);
    }

    @Transactional
    public StockReplenishResponse replenish(long id, StockReplenishCommand command) {
        return prizeStock.replenish(id, command);
    }

    public PageData<StockLogView> stockLogs(long id, PageQuery page) {
        return prizeReads.stockLogs(id, page);
    }
}
