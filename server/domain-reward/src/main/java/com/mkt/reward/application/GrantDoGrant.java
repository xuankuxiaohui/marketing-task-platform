package com.mkt.reward.application;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.domain.FailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Grant doGrant helper extracted from {@link GrantAppService} (R14-2).
 * Move-only: doGrant semantics unchanged.
 */
@Component
public class GrantDoGrant {

    private final GrantRecordStore grants;
    private final GrantResultAssembly assembly;
    private final GrantPersistence persistence;
    private final GrantExecute execute;

    @Autowired
    public GrantDoGrant(
            GrantRecordStore grants,
            GrantResultAssembly assembly,
            GrantPersistence persistence,
            GrantExecute execute) {
        this.grants = grants;
        this.assembly = assembly;
        this.persistence = persistence;
        this.execute = execute;
    }

    GrantResult doGrant(
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
}
