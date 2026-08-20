package com.mkt.reward.application;

import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.domain.ClaimModes;
import com.mkt.reward.domain.ClaimWindows;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.PrizeCost;
import com.mkt.reward.domain.PrizeCosts;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.domain.StockChangeTypes;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * §5.7 stock deduct + claim-limit COUNT inside the prize row lock (READ COMMITTED).
 */
@Service
public class PrizeStockService {

    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;
    private final GrantRecordStore grants;
    private final StockLogStore stockLogs;
    private final Clock clock;

    public PrizeStockService(
            PrizeStore prizes,
            PrizeCategoryStore categories,
            GrantRecordStore grants,
            StockLogStore stockLogs,
            Clock clock) {
        this.prizes = prizes;
        this.categories = categories;
        this.grants = grants;
        this.stockLogs = stockLogs;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockConsumeResult consume(long prizeId, long userId, GrantSource source, String sourceId) {
        PrizeEntity prize = prizes.getById(prizeId);
        if (prize == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        if (!PrizeStatuses.ENABLED.equals(prize.getStatus())) {
            throw new BusinessException(RewardErrorCodes.PRIZE_DISABLED);
        }
        int deducted = prizes.deductOne(prizeId);
        if (deducted != 1) {
            throw new BusinessException(RewardErrorCodes.STOCK_INSUFFICIENT);
        }
        PrizeEntity after = prizes.getById(prizeId);
        int remaining = after.getRemainingStock();
        int before = remaining + 1;
        assertClaimLimits(prize, userId);
        PrizeCategoryEntity category = categories.getByCode(prize.getCategoryCode());
        PrizeCost cost = PrizeCosts.snapshot(
                category == null ? null : category.getCostMode(),
                prize.getUnitCostFen(),
                RewardJson.map(prize.getTypeParams()));
        GrantRecordEntity record = newGrant(prize, userId, source, sourceId, cost);
        grants.insert(record);
        appendLog(
                prizeId,
                StockChangeTypes.GRANT,
                -1,
                before,
                remaining,
                source.name(),
                String.valueOf(record.getId()),
                null);
        return new StockConsumeResult(record.getId(), remaining, cost.costFen(), cost.faceFen());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void restore(long prizeId, long grantRecordId) {
        PrizeEntity prize = prizes.getById(prizeId);
        if (prize == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        int before = prize.getRemainingStock();
        int restored = prizes.restoreOne(prizeId);
        if (restored != 1) {
            throw new BusinessException(RewardErrorCodes.STOCK_INSUFFICIENT);
        }
        PrizeEntity after = prizes.getById(prizeId);
        grants.markPermanentFailed(grantRecordId);
        appendLog(
                prizeId,
                StockChangeTypes.ROLLBACK,
                1,
                before,
                after.getRemainingStock(),
                GrantSource.TASK_STEP.name(),
                String.valueOf(grantRecordId),
                null);
    }

    private void assertClaimLimits(PrizeEntity prize, long userId) {
        int daily = prize.getDailyClaimLimit() == null ? 0 : prize.getDailyClaimLimit();
        int total = prize.getTotalClaimLimit() == null ? 0 : prize.getTotalClaimLimit();
        if (daily > 0) {
            long used = grants.countActive(userId, prize.getId(), ClaimWindows.dailyStartUtc(clock));
            if (used >= daily) {
                throw new BusinessException(RewardErrorCodes.CLAIM_LIMIT_EXCEEDED, "当日限领已达上限");
            }
        }
        if (total > 0) {
            long used = grants.countActive(userId, prize.getId(), null);
            if (used >= total) {
                throw new BusinessException(RewardErrorCodes.CLAIM_LIMIT_EXCEEDED, "累计限领已达上限");
            }
        }
    }

    private GrantRecordEntity newGrant(
            PrizeEntity prize, long userId, GrantSource source, String sourceId, PrizeCost cost) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        GrantRecordEntity record = new GrantRecordEntity();
        record.setPrizeId(prize.getId());
        record.setPrizeCode(prize.getCode());
        record.setCategoryCode(prize.getCategoryCode());
        record.setFaceFen(cost.faceFen());
        record.setCostFen(cost.costFen());
        record.setReconStatus(GrantRecordStatuses.RECON_NONE);
        record.setUserId(userId);
        record.setGrantSource(source.name());
        record.setSourceId(sourceId);
        record.setStatus(
                ClaimModes.MANUAL.equals(prize.getClaimMode())
                        ? GrantRecordStatuses.WON
                        : GrantRecordStatuses.GRANTED);
        record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        record.setRetryCount(0);
        record.setSimulated(0);
        record.setGrantedAt(ClaimModes.MANUAL.equals(prize.getClaimMode()) ? null : now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
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

    public record StockConsumeResult(long grantRecordId, int remainingStock, int costFen, Integer faceFen) {}
}
