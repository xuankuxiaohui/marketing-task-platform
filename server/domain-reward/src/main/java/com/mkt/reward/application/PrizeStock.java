package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.StockReplenishCommand;
import com.mkt.reward.domain.StockChangeTypes;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.response.StockReplenishResponse;
import com.mkt.reward.support.RewardOperator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Replenish entry extracted from {@link PrizeAppService} (R33-3).
 * Move-only: replenish / appendLog semantics unchanged. {@code require} is a stock-side copy;
 * the facade keeps the shared require used by get / delete / stockLogs.
 */
@Component
public class PrizeStock {

    private final PrizeStore prizes;
    private final StockLogStore stockLogs;
    private final Clock clock;

    @Autowired
    public PrizeStock(PrizeStore prizes, StockLogStore stockLogs, Clock clock) {
        this.prizes = prizes;
        this.stockLogs = stockLogs;
        this.clock = clock;
    }

    StockReplenishResponse replenish(long id, StockReplenishCommand command) {
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
