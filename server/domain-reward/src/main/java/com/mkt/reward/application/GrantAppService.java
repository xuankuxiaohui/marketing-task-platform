package com.mkt.reward.application;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.BypassRule;
import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.PermanentGrantReason;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.ManualGrantCommand;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.ClaimModes;
import com.mkt.reward.domain.ClaimWindows;
import com.mkt.reward.domain.FailReasons;
import com.mkt.reward.domain.GrantLimits;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.PrizeCost;
import com.mkt.reward.domain.PrizeCosts;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.domain.StockChangeTypes;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.StockLogEntity;
import com.mkt.reward.response.GrantRetryResponse;
import com.mkt.reward.response.ManualGrantResponse;
import com.mkt.reward.support.GrantSourceIds;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardOperator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** RewardPort.grant + admin retry / manual-grant (design §5.6.2 / §4.5). */
@Service
public class GrantAppService {

    static final int RETRY_BATCH = 100;

    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;
    private final GrantRecordStore grants;
    private final StockLogStore stockLogs;
    private final UserAttributePort users;
    private final RiskCheckPort risk;
    private final EventPublisher events;
    private final GrantFailureLedger failures;
    private final FulfillmentService fulfillment;
    private final ObjectProvider<GrantStepResumer> resumerProvider;
    private final GrantStepResumer resumerDirect;
    private final Clock clock;

    @Autowired
    public GrantAppService(
            PrizeStore prizes,
            PrizeCategoryStore categories,
            GrantRecordStore grants,
            StockLogStore stockLogs,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<RiskCheckPort> risk,
            ObjectProvider<EventPublisher> events,
            GrantFailureLedger failures,
            FulfillmentService fulfillment,
            ObjectProvider<GrantStepResumer> resumer,
            Clock clock) {
        this.prizes = prizes;
        this.categories = categories;
        this.grants = grants;
        this.stockLogs = stockLogs;
        this.users = users.getIfAvailable();
        this.risk = risk.getIfAvailable();
        this.events = events.getIfAvailable();
        this.failures = failures;
        this.fulfillment = fulfillment;
        this.resumerProvider = resumer;
        this.resumerDirect = null;
        this.clock = clock;
    }

    public GrantAppService(
            PrizeStore prizes,
            PrizeCategoryStore categories,
            GrantRecordStore grants,
            StockLogStore stockLogs,
            UserAttributePort users,
            RiskCheckPort risk,
            EventPublisher events,
            GrantFailureLedger failures,
            FulfillmentService fulfillment,
            GrantStepResumer resumer,
            Clock clock) {
        this.prizes = prizes;
        this.categories = categories;
        this.grants = grants;
        this.stockLogs = stockLogs;
        this.users = users;
        this.risk = risk;
        this.events = events;
        this.failures = failures;
        this.fulfillment = fulfillment;
        this.resumerProvider = null;
        this.resumerDirect = resumer;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = PermanentGrantException.class)
    public GrantResult grant(
            long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx) {
        if (grantSource == null || sourceId == null || sourceId.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        GrantContext context = ctx == null ? GrantContext.defaults() : ctx;
        return doGrant(prizeId, userId, grantSource, sourceId, context, false);
    }

    private GrantResult doGrant(
            long prizeId,
            long userId,
            GrantSource grantSource,
            String sourceId,
            GrantContext context,
            boolean retryPermanent) {
        GrantRecordEntity existing = grants.getByIdempotent(grantSource.name(), sourceId, prizeId);
        if (existing != null
                && !GrantRecordStatuses.RETRY_PENDING.equals(existing.getStatus())
                && !(retryPermanent && GrantRecordStatuses.PERMANENT_FAILED.equals(existing.getStatus()))) {
            return toResult(existing, true);
        }
        if (retryPermanent
                && existing != null
                && GrantRecordStatuses.PERMANENT_FAILED.equals(existing.getStatus())) {
            existing.setRetryCount(0);
            existing.setFailReason(null);
        }
        try {
            return execute(prizeId, userId, grantSource, sourceId, context, existing);
        } catch (IdempotentHit hit) {
            return toResult(hit.winner, true);
        } catch (RetryableGrantException ex) {
            leaveRetry(prizeId, userId, grantSource, sourceId, context, existing, ex.reason().name());
            throw ex;
        } catch (PermanentGrantException ex) {
            persistPermanent(prizeId, userId, grantSource, sourceId, context, existing, ex.reason().name());
            throw ex;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            leaveRetry(
                    prizeId,
                    userId,
                    grantSource,
                    sourceId,
                    context,
                    existing,
                    FailReasons.SYSTEM_ERROR);
            throw new RetryableGrantException(RetryableGrantReason.SYSTEM_ERROR);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GrantRetryResponse retry(long recordId) {
        GrantRecordEntity row = grants.getById(recordId);
        if (row == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        if (!GrantRecordStatuses.RETRY_PENDING.equals(row.getStatus())
                && !GrantRecordStatuses.PERMANENT_FAILED.equals(row.getStatus())) {
            throw new BusinessException(RewardErrorCodes.GRANT_NOT_RETRYABLE);
        }
        GrantSource source = GrantSource.valueOf(row.getGrantSource());
        GrantContext ctx = new GrantContext(null, List.of(), RewardOperator.optionalUserId(), simulated(row), null);
        GrantResult result;
        try {
            result = doGrant(row.getPrizeId(), row.getUserId(), source, row.getSourceId(), ctx, true);
        } catch (RetryableGrantException ex) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            GrantRecordEntity fresh = grants.getById(recordId);
            if (fresh == null) {
                throw ex;
            }
            int retryCount = fresh.getRetryCount() == null ? 0 : fresh.getRetryCount();
            return new GrantRetryResponse(fresh.getStatus(), retryCount);
        } catch (PermanentGrantException ex) {
            GrantRecordEntity fresh = grants.getById(recordId);
            if (fresh == null) {
                throw ex;
            }
            int retryCount = fresh.getRetryCount() == null ? 0 : fresh.getRetryCount();
            return new GrantRetryResponse(fresh.getStatus(), retryCount);
        }
        if (result.status() == GrantStatus.GRANTED || result.status() == GrantStatus.WON) {
            resume(source, row.getSourceId());
        }
        GrantRecordEntity fresh = grants.getById(recordId);
        int retryCount = fresh == null || fresh.getRetryCount() == null ? 0 : fresh.getRetryCount();
        return new GrantRetryResponse(result.status().name(), retryCount);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = PermanentGrantException.class)
    public ManualGrantResponse manualGrant(ManualGrantCommand command) {
        if (command.userId() == null || command.prizeId() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "补发原因必填");
        }
        long operatorId = RewardOperator.requireUserId();
        List<BypassRule> bypass = parseBypass(command.bypassRules());
        GrantContext ctx = new GrantContext(command.reason().trim(), bypass, operatorId, false, null);
        String sourceId = GrantSourceIds.nextManual(clock);
        try {
            GrantResult result =
                    doGrant(command.prizeId(), command.userId(), GrantSource.MANUAL_GRANT, sourceId, ctx, false);
            return new ManualGrantResponse(result.recordId(), result.status().name());
        } catch (PermanentGrantException ex) {
            throw mapPermanent(ex);
        } catch (RetryableGrantException ex) {
            throw mapRetryable(ex);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = PermanentGrantException.class)
    public ManualGrantResponse reconManualGrant(long userId, long prizeId, String reason, String sourceId) {
        if (reason == null || reason.isBlank() || sourceId == null || sourceId.isBlank()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        long operatorId = RewardOperator.requireUserId();
        GrantContext ctx = new GrantContext(reason.trim(), List.of(), operatorId, false, null);
        try {
            GrantResult result = doGrant(prizeId, userId, GrantSource.MANUAL_GRANT, sourceId, ctx, false);
            return new ManualGrantResponse(result.recordId(), result.status().name());
        } catch (PermanentGrantException ex) {
            throw mapPermanent(ex);
        } catch (RetryableGrantException ex) {
            throw mapRetryable(ex);
        }
    }

    public List<GrantRecordEntity> listDueRetry() {
        return grants.listDueRetry(RewardTime.toUtc(clock.instant()), RETRY_BATCH);
    }

    public int retryDue() {
        List<GrantRecordEntity> due = listDueRetry();
        int ran = 0;
        for (GrantRecordEntity row : due) {
            try {
                GrantSource source = GrantSource.valueOf(row.getGrantSource());
                GrantContext ctx = new GrantContext(null, List.of(), null, simulated(row), null);
                GrantResult result = grant(row.getPrizeId(), row.getUserId(), source, row.getSourceId(), ctx);
                if (result.status() == GrantStatus.GRANTED || result.status() == GrantStatus.WON) {
                    resume(source, row.getSourceId());
                }
                ran++;
            } catch (RuntimeException ex) {
                ran++;
            }
        }
        return ran;
    }

    private GrantResult execute(
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
        } catch (IdempotentHit hit) {
            prizes.restoreOne(prizeId);
            throw hit;
        } catch (RuntimeException ex) {
            prizes.restoreOne(prizeId);
            throw ex;
        }
    }

    private GrantResult afterDeduct(
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
            throw new IdempotentHit(raced);
        }
        PrizeEntity after = prizes.getById(prizeId);
        int remaining = after.getRemainingStock();
        int before = remaining + 1;
        assertClaimLimits(prize, userId, existing);
        GrantContext effective = GrantLimits.allowBypass(grantSource) ? ctx : stripBypass(ctx);
        if (!GrantLimits.regionOk(prize, attrs, effective)
                || !GrantLimits.levelOk(prize, attrs, effective)
                || !GrantLimits.tagOk(prize, attrs, effective)) {
            throw new BusinessException(RewardErrorCodes.GRANT_COMBO_INVALID);
        }
        checkRisk(userId, grantSource, ctx);
        PrizeCost cost = PrizeCosts.snapshot(
                category == null ? null : category.getCostMode(),
                prize.getUnitCostFen(),
                RewardJson.map(prize.getTypeParams()));
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        GrantRecordEntity record = existing != null ? existing : (raced != null ? raced : new GrantRecordEntity());
        String fromStatus = record.getStatus();
        fillBase(record, prizeId, prize, userId, grantSource, sourceId, cost, ctx, now);
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
        persistSuccess(record, fromStatus);
        if (auto) {
            fulfillment.emitArrived(record);
            fulfillment.armThirdPartyStub(record, prize, category);
        }
        appendStockLog(
                prizeId,
                grantSource == GrantSource.MANUAL_GRANT ? StockChangeTypes.MANUAL_GRANT : StockChangeTypes.GRANT,
                -1,
                before,
                remaining,
                grantSource.name(),
                String.valueOf(record.getId()),
                ctx.operatorId());
        appendSuccess(record, grantSource);
        return toResult(record, false);
    }

    private void persistSuccess(GrantRecordEntity record, String fromStatus) {
        if (record.getId() == null) {
            try {
                grants.insert(record);
            } catch (DuplicateKeyException ex) {
                GrantRecordEntity winner =
                        grants.getByIdempotent(record.getGrantSource(), record.getSourceId(), record.getPrizeId());
                if (winner != null
                        && !GrantRecordStatuses.RETRY_PENDING.equals(winner.getStatus())
                        && !GrantRecordStatuses.PERMANENT_FAILED.equals(winner.getStatus())) {
                    throw new IdempotentHit(winner);
                }
                throw ex;
            }
            return;
        }
        if (GrantRecordStatuses.RETRY_PENDING.equals(fromStatus)
                || GrantRecordStatuses.PERMANENT_FAILED.equals(fromStatus)) {
            int affected = grants.updateIfStatus(record, fromStatus);
            if (affected != 1) {
                GrantRecordEntity winner =
                        grants.getByIdempotent(record.getGrantSource(), record.getSourceId(), record.getPrizeId());
                if (winner != null
                        && (GrantRecordStatuses.GRANTED.equals(winner.getStatus())
                                || GrantRecordStatuses.WON.equals(winner.getStatus()))) {
                    throw new IdempotentHit(winner);
                }
                throw new RetryableGrantException(RetryableGrantReason.SYSTEM_ERROR);
            }
            return;
        }
        grants.update(record);
    }

    private void leaveRetry(
            long prizeId,
            long userId,
            GrantSource grantSource,
            String sourceId,
            GrantContext ctx,
            GrantRecordEntity existing,
            String failReason) {
        GrantRecordEntity draft = existing == null ? new GrantRecordEntity() : existing;
        PrizeEntity prize = prizes.getByIdIncludingDeleted(prizeId);
        fillBase(
                draft,
                prizeId,
                prize,
                userId,
                grantSource,
                sourceId,
                new PrizeCost(0, null),
                ctx,
                RewardTime.toUtc(clock.instant()));
        draft.setFailReason(failReason);
        boolean increment = existing != null && GrantRecordStatuses.RETRY_PENDING.equals(existing.getStatus());
        failures.leaveRetryPending(draft, increment);
    }

    private void persistPermanent(
            long prizeId,
            long userId,
            GrantSource grantSource,
            String sourceId,
            GrantContext ctx,
            GrantRecordEntity existing,
            String failReason) {
        GrantRecordEntity draft = existing == null ? new GrantRecordEntity() : existing;
        PrizeEntity prize = prizes.getByIdIncludingDeleted(prizeId);
        fillBase(
                draft,
                prizeId,
                prize,
                userId,
                grantSource,
                sourceId,
                new PrizeCost(0, null),
                ctx,
                RewardTime.toUtc(clock.instant()));
        draft.setFailReason(failReason);
        failures.markPermanent(draft);
    }

    private void fillBase(
            GrantRecordEntity record,
            long prizeId,
            PrizeEntity prize,
            long userId,
            GrantSource grantSource,
            String sourceId,
            PrizeCost cost,
            GrantContext ctx,
            LocalDateTime now) {
        record.setPrizeId(prize != null && prize.getId() != null ? prize.getId() : prizeId);
        record.setPrizeCode(prize == null ? "missing" : prize.getCode());
        record.setCategoryCode(prize == null ? "MISSING" : prize.getCategoryCode());
        record.setFaceFen(cost.faceFen());
        record.setCostFen(cost.costFen());
        record.setReconStatus(GrantRecordStatuses.RECON_NONE);
        record.setUserId(userId);
        record.setGrantSource(grantSource.name());
        record.setSourceId(sourceId);
        if (record.getFulfillmentStatus() == null) {
            record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        }
        if (record.getRetryCount() == null) {
            record.setRetryCount(0);
        }
        record.setSimulated(ctx != null && ctx.simulated() ? 1 : 0);
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        record.setUpdatedAt(now);
    }

    private void assertClaimLimits(PrizeEntity prize, long userId, GrantRecordEntity existing) {
        int daily = prize.getDailyClaimLimit() == null ? 0 : prize.getDailyClaimLimit();
        int total = prize.getTotalClaimLimit() == null ? 0 : prize.getTotalClaimLimit();
        long self = occupyingSlot(existing) ? 1 : 0;
        if (daily > 0) {
            long used = grants.countActive(userId, prize.getId(), ClaimWindows.dailyStartUtc(clock)) - self;
            if (used >= daily) {
                throw new BusinessException(RewardErrorCodes.CLAIM_LIMIT_EXCEEDED, "当日限领已达上限");
            }
        }
        if (total > 0) {
            long used = grants.countActive(userId, prize.getId(), null) - self;
            if (used >= total) {
                throw new BusinessException(RewardErrorCodes.CLAIM_LIMIT_EXCEEDED, "累计限领已达上限");
            }
        }
    }

    private static boolean occupyingSlot(GrantRecordEntity existing) {
        return existing != null && GrantRecordStatuses.LIMIT_COUNT.contains(existing.getStatus());
    }

    private void checkRisk(long userId, GrantSource grantSource, GrantContext ctx) {
        if (risk == null) {
            return;
        }
        Long elapsed = ctx.simulated() || grantSource != GrantSource.TASK_STEP ? null : ctx.elapsedSeconds();
        RiskVerdict verdict = risk.check(
                RiskScene.GRANT,
                new RiskSubject(userId, clientIp(ctx), clientDevice(ctx), elapsed, ctx.simulated()));
        if (verdict.action() == RiskAction.REJECT || verdict.action() == RiskAction.SILENT_REJECT) {
            if (grantSource == GrantSource.MANUAL_GRANT) {
                throw new BusinessException(RewardErrorCodes.RISK_BLOCKED_ACCOUNT);
            }
            throw new BusinessException(RewardErrorCodes.RISK_BLOCKED_GENERIC);
        }
    }

    private static String clientIp(GrantContext ctx) {
        String ip = ctx.ip();
        if (ip == null || ip.isBlank() || "0.0.0.0".equals(ip)) {
            return null;
        }
        return ip;
    }

    private static String clientDevice(GrantContext ctx) {
        String device = ctx.deviceId();
        return device == null || device.isBlank() ? null : device;
    }

    private void appendStockLog(
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

    private void appendSuccess(GrantRecordEntity record, GrantSource source) {
        if (events == null) {
            return;
        }
        events.append(
                EventCodes.REWARD_GRANT_SUCCESS,
                "rwd_grant_record",
                String.valueOf(record.getId()),
                Map.of(
                        "recordId", record.getId(),
                        "prizeId", record.getPrizeId(),
                        "grantSource", source.name(),
                        "userId", record.getUserId(),
                        "simulated", simulated(record)));
    }

    private void resume(GrantSource source, String sourceId) {
        GrantStepResumer resumer = resumerDirect;
        if (resumer == null && resumerProvider != null) {
            resumer = resumerProvider.getIfAvailable();
        }
        if (resumer == null) {
            return;
        }
        resumer.resume(source, sourceId);
    }

    private static BusinessException mapPermanent(PermanentGrantException ex) {
        return switch (ex.reason()) {
            case PRIZE_DISABLED -> new BusinessException(RewardErrorCodes.PRIZE_DISABLED);
            case PRIZE_DELETED -> new BusinessException(CommonErrorCodes.NOT_FOUND);
            case USER_INVALID -> new BusinessException(RewardErrorCodes.GRANT_COMBO_INVALID);
        };
    }

    private static BusinessException mapRetryable(RetryableGrantException ex) {
        if (ex.reason() == RetryableGrantReason.STOCK_INSUFFICIENT) {
            return new BusinessException(RewardErrorCodes.STOCK_INSUFFICIENT);
        }
        return new BusinessException(CommonErrorCodes.SERVER_ERROR);
    }

    private static GrantContext stripBypass(GrantContext ctx) {
        return new GrantContext(
                ctx.reason(),
                List.of(),
                ctx.operatorId(),
                ctx.simulated(),
                ctx.elapsedSeconds(),
                ctx.ip(),
                ctx.deviceId());
    }

    private static List<BypassRule> parseBypass(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<BypassRule> rules = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            try {
                rules.add(BypassRule.valueOf(item.trim()));
            } catch (IllegalArgumentException ignored) {
                // only REGION/LEVEL/TAG are defined
            }
        }
        return List.copyOf(rules);
    }

    private static boolean simulated(GrantRecordEntity row) {
        return row.getSimulated() != null && row.getSimulated() == 1;
    }

    private static GrantResult toResult(GrantRecordEntity row, boolean hitIdempotent) {
        return new GrantResult(
                row.getId() == null ? 0L : row.getId(),
                GrantStatus.valueOf(row.getStatus()),
                FulfillmentStatus.valueOf(row.getFulfillmentStatus()),
                row.getPrizeId(),
                hitIdempotent);
    }

    private static final class IdempotentHit extends RuntimeException {
        private final GrantRecordEntity winner;

        private IdempotentHit(GrantRecordEntity winner) {
            this.winner = winner;
        }
    }
}
