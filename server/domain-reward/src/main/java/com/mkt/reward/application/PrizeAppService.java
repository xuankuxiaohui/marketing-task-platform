package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.command.StockReplenishCommand;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.domain.StockChangeTypes;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.query.PrizeQuery;
import com.mkt.reward.response.PrizeImpactResponse;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.response.StockLogView;
import com.mkt.reward.response.StockReplenishResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardOperator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrizeAppService {

    private final PrizeStore prizes;
    private final StockLogStore stockLogs;
    private final SnapshotPrizeLookup snapshots;
    private final Clock clock;
    private final PrizeViews views;
    private final PrizeSave prizeSave;
    private final PrizeLifecycle prizeLifecycle;

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
            PrizeLifecycle prizeLifecycle) {
        this.prizes = prizes;
        this.stockLogs = stockLogs;
        this.snapshots = snapshots;
        this.clock = clock;
        this.views = views;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.prizeSave = prizeSave != null ? prizeSave : new PrizeSave(prizes, clock, validate, apply, views);
        this.prizeLifecycle = prizeLifecycle != null
                ? prizeLifecycle
                : new PrizeLifecycle(prizes, snapshots, clock, validate);
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
                null);
    }

    public PrizeResponse get(long id) {
        return views.toResponse(require(id));
    }

    public PageData<PrizeResponse> page(PrizeQuery query) {
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
        PrizeEntity existing = require(id);
        if (!PrizeStatuses.DRAFT.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅草稿态可删除");
        }
        if (snapshots.referencedByAnySnapshot(id)) {
            throw new BusinessException(RewardErrorCodes.PRIZE_REFERENCED_BY_SNAPSHOT);
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
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
        PrizeEntity existing = require(id);
        if (command == null || command.amount() == null || command.amount() <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "回补数量必须为正整数");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "回补原因必填");
        }
        int before = existing.getRemainingStock();
        int affected = prizes.replenish(id, command.amount());
        if (affected != 1) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        PrizeEntity after = require(id);
        appendLog(
                id,
                StockChangeTypes.REPLENISH,
                command.amount(),
                before,
                after.getRemainingStock(),
                null,
                command.reason().trim(),
                RewardOperator.optionalUserId());
        return new StockReplenishResponse(after.getRemainingStock());
    }

    public PageData<StockLogView> stockLogs(long id, PageQuery page) {
        require(id);
        long total = stockLogs.countByPrize(id);
        List<StockLogEntity> rows = stockLogs.listByPrize(id, page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(views::toLogView).toList());
    }

    private PrizeEntity require(long id) {
        PrizeEntity existing = prizes.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private void appendLog(
            long prizeId,
            String changeType,
            int amount,
            int before,
            int after,
            String bizSource,
            String bizId,
            Long operatorId) {
        StockLogEntity log = new StockLogEntity();
        log.setPrizeId(prizeId);
        log.setChangeType(changeType);
        log.setAmount(amount);
        log.setBeforeValue(before);
        log.setAfterValue(after);
        log.setBizSource(bizSource);
        log.setBizId(bizId);
        log.setOperatorId(operatorId);
        log.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        stockLogs.insert(log);
    }
}
