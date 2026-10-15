package com.mkt.reward.application;

import com.mkt.contract.BypassRule;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.ManualGrantCommand;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.FailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.response.GrantRetryResponse;
import com.mkt.reward.response.ManualGrantResponse;
import com.mkt.reward.support.GrantSourceIds;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardOperator;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** RewardPort.grant + admin retry / manual-grant (design §5.6.2 / §4.5). */
@Service
public class GrantAppService {

    static final int RETRY_BATCH = 100;

    private final GrantRecordStore grants;
    private final ObjectProvider<GrantStepResumer> resumerProvider;
    private final GrantStepResumer resumerDirect;
    private final Clock clock;
    private final GrantChecks checks;
    private final GrantResultAssembly assembly;
    private final GrantPersistence persistence;
    private final GrantExecute execute;

    @Autowired
    public GrantAppService(
            GrantRecordStore grants,
            ObjectProvider<GrantStepResumer> resumer,
            Clock clock,
            GrantChecks checks,
            GrantResultAssembly assembly,
            GrantPersistence persistence,
            GrantExecute execute) {
        this.grants = grants;
        this.resumerProvider = resumer;
        this.resumerDirect = null;
        this.clock = clock;
        this.checks = checks;
        this.assembly = assembly;
        this.persistence = persistence;
        this.execute = execute;
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
        this.grants = grants;
        this.resumerProvider = null;
        this.resumerDirect = resumer;
        this.clock = clock;
        this.checks = new GrantChecks(grants, risk, clock);
        this.assembly = new GrantResultAssembly(events, prizes);
        this.persistence = new GrantPersistence(grants, prizes, this.assembly, failures, clock);
        this.execute = new GrantExecute(
                prizes,
                categories,
                grants,
                users,
                fulfillment,
                clock,
                this.checks,
                new GrantStockLogs(stockLogs, clock),
                this.assembly,
                this.persistence);
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
            return assembly.toResult(existing, true);
        }
        if (retryPermanent
                && existing != null
                && GrantRecordStatuses.PERMANENT_FAILED.equals(existing.getStatus())) {
            existing.setRetryCount(0);
            existing.setFailReason(null);
        }
        try {
            return execute.execute(prizeId, userId, grantSource, sourceId, context, existing);
        } catch (GrantPersistence.IdempotentHit hit) {
            return assembly.toResult(hit.winner, true);
        } catch (RetryableGrantException ex) {
            persistence.leaveRetry(prizeId, userId, grantSource, sourceId, context, existing, ex.reason().name());
            throw ex;
        } catch (PermanentGrantException ex) {
            persistence.persistPermanent(
                    prizeId, userId, grantSource, sourceId, context, existing, ex.reason().name());
            throw ex;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            persistence.leaveRetry(
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
        List<BypassRule> bypass = checks.parseBypass(command.bypassRules());
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

    private static boolean simulated(GrantRecordEntity row) {
        return row.getSimulated() != null && row.getSimulated() == 1;
    }
}
