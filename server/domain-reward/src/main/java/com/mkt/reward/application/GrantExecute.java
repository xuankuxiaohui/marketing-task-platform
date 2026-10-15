package com.mkt.reward.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.PermanentGrantReason;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ClaimModes;
import com.mkt.reward.domain.GrantLimits;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.PrizeCost;
import com.mkt.reward.domain.PrizeCosts;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.domain.StockChangeTypes;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Grant execute helpers extracted from {@link GrantAppService} (R14-1).
 * Move-only: execute / afterDeduct semantics unchanged.
 */
@Component
public class GrantExecute {

    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;
    private final GrantRecordStore grants;
    private final UserAttributePort users;
    private final FulfillmentService fulfillment;
    private final Clock clock;
    private final GrantChecks checks;
    private final GrantStockLogs stockLogs;
    private final GrantResultAssembly assembly;
    private final GrantPersistence persistence;

    @Autowired
    public GrantExecute(
            PrizeStore prizes,
            PrizeCategoryStore categories,
            GrantRecordStore grants,
            ObjectProvider<UserAttributePort> users,
            FulfillmentService fulfillment,
            Clock clock,
            GrantChecks checks,
            GrantStockLogs stockLogs,
            GrantResultAssembly assembly,
            GrantPersistence persistence) {
        this(
                prizes,
                categories,
                grants,
                users.getIfAvailable(),
                fulfillment,
                clock,
                checks,
                stockLogs,
                assembly,
                persistence);
    }

    public GrantExecute(
            PrizeStore prizes,
            PrizeCategoryStore categories,
            GrantRecordStore grants,
            UserAttributePort users,
            FulfillmentService fulfillment,
            Clock clock,
            GrantChecks checks,
            GrantStockLogs stockLogs,
            GrantResultAssembly assembly,
            GrantPersistence persistence) {
        this.prizes = prizes;
        this.categories = categories;
        this.grants = grants;
        this.users = users;
        this.fulfillment = fulfillment;
        this.clock = clock;
        this.checks = checks;
        this.stockLogs = stockLogs;
        this.assembly = assembly;
        this.persistence = persistence;
    }

    GrantResult execute(
            long prizeId,
            long userId,
            GrantSource grantSource,
            String sourceId,
            GrantContext ctx,
            GrantRecordEntity existing) {
        UserAttributes attrs = users == null ? UserAttributes.notFound() : users.attributes(userId);
        if (attrs.accountStatus() != AccountStatus.ACTIVE) {
            throw new PermanentGrantException(PermanentGrantReason.USER_INVALID);
        }
        PrizeEntity prize = prizes.getByIdIncludingDeleted(prizeId);
        if (prize == null || prize.deletedFlag()) {
            throw new PermanentGrantException(PermanentGrantReason.PRIZE_DELETED);
        }
        if (!PrizeStatuses.ENABLED.equals(prize.getStatus())) {
            throw new PermanentGrantException(PermanentGrantReason.PRIZE_DISABLED);
        }
        PrizeCategoryEntity category = categories.getByCode(prize.getCategoryCode());
        int deducted = prizes.deductOne(prizeId);
        if (deducted != 1) {
            if (grantSource == GrantSource.MANUAL_GRANT) {
                throw new BusinessException(RewardErrorCodes.STOCK_INSUFFICIENT);
            }
            throw new RetryableGrantException(RetryableGrantReason.STOCK_INSUFFICIENT);
        }
        try {
            return afterDeduct(
                    prizeId, userId, grantSource, sourceId, ctx, existing, prize, category, attrs);
        } catch (GrantPersistence.IdempotentHit hit) {
            prizes.restoreOne(prizeId);
            throw hit;
        } catch (RuntimeException ex) {
            prizes.restoreOne(prizeId);
            throw ex;
        }
    }

    GrantResult afterDeduct(
            long prizeId,
            long userId,
            GrantSource grantSource,
            String sourceId,
            GrantContext ctx,
            GrantRecordEntity existing,
            PrizeEntity prize,
            PrizeCategoryEntity category,
            UserAttributes attrs) {
        GrantRecordEntity raced = grants.getByIdempotent(grantSource.name(), sourceId, prizeId);
        if (raced != null && !GrantRecordStatuses.RETRY_PENDING.equals(raced.getStatus())) {
            throw new GrantPersistence.IdempotentHit(raced);
        }
        PrizeEntity after = prizes.getById(prizeId);
        int remaining = after.getRemainingStock();
        int before = remaining + 1;
        checks.assertClaimLimits(prize, userId, existing);
        GrantContext effective = GrantLimits.allowBypass(grantSource) ? ctx : checks.stripBypass(ctx);
        if (!GrantLimits.regionOk(prize, attrs, effective)
                || !GrantLimits.levelOk(prize, attrs, effective)
                || !GrantLimits.tagOk(prize, attrs, effective)) {
            throw new BusinessException(RewardErrorCodes.GRANT_COMBO_INVALID);
        }
        checks.checkRisk(userId, grantSource, ctx);
        PrizeCost cost = PrizeCosts.snapshot(
                category == null ? null : category.getCostMode(),
                prize.getUnitCostFen(),
                RewardJson.map(prize.getTypeParams()));
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        GrantRecordEntity record = existing != null ? existing : (raced != null ? raced : new GrantRecordEntity());
        String fromStatus = record.getStatus();
        assembly.fillBase(record, prizeId, prize, userId, grantSource, sourceId, cost, ctx, now);
        boolean auto = ClaimModes.AUTO.equals(prize.getClaimMode());
        if (auto) {
            record.setStatus(GrantRecordStatuses.GRANTED);
            record.setGrantedAt(now);
            record.setExpireAt(null);
            fulfillment.start(record, prize, category);
        } else {
            record.setStatus(GrantRecordStatuses.WON);
            record.setGrantedAt(null);
            if (prize.getExpireHours() != null) {
                record.setExpireAt(now.plusHours(prize.getExpireHours()));
            }
            record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        }
        record.setFailReason(null);
        record.setNextRetryAt(null);
        persistence.persistSuccess(record, fromStatus);
        if (auto) {
            fulfillment.emitArrived(record);
            fulfillment.armThirdPartyStub(record, prize, category);
        }
        stockLogs.appendStockLog(
                prizeId,
                grantSource == GrantSource.MANUAL_GRANT ? StockChangeTypes.MANUAL_GRANT : StockChangeTypes.GRANT,
                -1,
                before,
                remaining,
                grantSource.name(),
                String.valueOf(record.getId()),
                ctx.operatorId());
        assembly.appendSuccess(record, grantSource);
        return assembly.toResult(record, false);
    }
}
