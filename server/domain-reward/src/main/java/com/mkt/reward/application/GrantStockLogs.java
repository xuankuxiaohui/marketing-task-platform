package com.mkt.reward.application;

import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.entity.StockLogEntity;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stock-log writer extracted from {@link GrantAppService} (R2-2).
 * Move-only: appendStockLog semantics unchanged.
 */
@Component
public class GrantStockLogs {

    private final StockLogStore stockLogs;
    private final Clock clock;

    @Autowired
    public GrantStockLogs(StockLogStore stockLogs, Clock clock) {
        this.stockLogs = stockLogs;
        this.clock = clock;
    }

    void appendStockLog(
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
        log.setCreatedAt(RewardTime.toUtc(clock.instant()));
        stockLogs.insert(log);
    }
}
