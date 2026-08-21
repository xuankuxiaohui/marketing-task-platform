package com.mkt.reward.application;

import com.mkt.contract.RetryableGrantException;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.LockKeys;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.response.ClaimResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardRuntimeSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** User claim WON / RETRY_PENDING → GRANTED (design §5.6.1 / §6.8). */
@Service
public class ClaimAppService {

    static final int BATCH = 100;

    private final GrantRecordStore grants;
    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;
    private final FulfillmentService fulfillment;
    private final PlatformLock locks;
    private final RewardRuntimeSettings settings;
    private final Clock clock;

    @Autowired
    public ClaimAppService(
            GrantRecordStore grants,
            PrizeStore prizes,
            PrizeCategoryStore categories,
            FulfillmentService fulfillment,
            ObjectProvider<PlatformLock> locks,
            RewardRuntimeSettings settings,
            Clock clock) {
        this(grants, prizes, categories, fulfillment, locks.getIfAvailable(), settings, clock);
    }

    public ClaimAppService(
            GrantRecordStore grants,
            PrizeStore prizes,
            PrizeCategoryStore categories,
            FulfillmentService fulfillment,
            PlatformLock locks,
            RewardRuntimeSettings settings,
            Clock clock) {
        this.grants = grants;
        this.prizes = prizes;
        this.categories = categories;
        this.fulfillment = fulfillment;
        this.locks = locks;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = {BusinessException.class, RetryableGrantException.class})
    public ClaimResponse claim(long recordId, long userId) {
        GrantRecordEntity row = grants.getById(recordId);
        if (row == null || row.getUserId() == null || row.getUserId() != userId) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        if (GrantRecordStatuses.GRANTED.equals(row.getStatus())) {
            return new ClaimResponse(row.getStatus(), row.getFulfillmentStatus());
        }
        if (row.getExpireAt() != null && !row.getExpireAt().isAfter(now)) {
            grants.casExpireOne(recordId, now);
            throw new BusinessException(RewardErrorCodes.CLAIM_EXPIRED);
        }
        if (GrantRecordStatuses.RETRY_PENDING.equals(row.getStatus())
                && (row.getRetryCount() == null ? 0 : row.getRetryCount()) >= settings.claimRetryMax()) {
            grants.casPermanentFromRetry(recordId, now, settings.claimRetryMax());
            throw new BusinessException(RewardErrorCodes.CLAIM_NOT_WON);
        }
        if (!GrantRecordStatuses.WON.equals(row.getStatus())
                && !GrantRecordStatuses.RETRY_PENDING.equals(row.getStatus())) {
            if (GrantRecordStatuses.CLAIMING.equals(row.getStatus())) {
                throw new BusinessException(RewardErrorCodes.CLAIM_CONFLICT);
            }
            throw new BusinessException(RewardErrorCodes.CLAIM_NOT_WON);
        }
        LockAcquire acquired = tryLock(recordId);
        if (acquired == LockAcquire.BUSY) {
            throw new BusinessException(RewardErrorCodes.CLAIM_CONFLICT);
        }
        try {
            return finishClaim(recordId, now);
        } finally {
            unlock(recordId, acquired);
        }
    }

    public int rollbackClaimingTimeout() {
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        LocalDateTime cutoff = now.minusSeconds(settings.claimingTimeoutSeconds());
        int ran = 0;
        for (GrantRecordEntity row : grants.listClaimingTimeout(cutoff, BATCH)) {
            ran += grants.rollbackClaiming(row.getId(), now);
        }
        return ran;
    }

    public int expireDue() {
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        int ran = 0;
        for (GrantRecordEntity row : grants.listPrizeExpireDue(now, BATCH)) {
            ran += grants.casExpireOne(row.getId(), now);
        }
        return ran;
    }

    private ClaimResponse finishClaim(long recordId, LocalDateTime now) {
        int started = grants.casClaimStart(recordId, now, settings.claimRetryMax());
        if (started != 1) {
            GrantRecordEntity fresh = grants.getById(recordId);
            if (fresh != null && GrantRecordStatuses.GRANTED.equals(fresh.getStatus())) {
                return new ClaimResponse(fresh.getStatus(), fresh.getFulfillmentStatus());
            }
            if (fresh != null && fresh.getExpireAt() != null && !fresh.getExpireAt().isAfter(now)) {
                grants.casExpireOne(recordId, now);
                throw new BusinessException(RewardErrorCodes.CLAIM_EXPIRED);
            }
            throw new BusinessException(RewardErrorCodes.CLAIM_CONFLICT);
        }
        GrantRecordEntity row = grants.getById(recordId);
        PrizeEntity prize = prizes.getByIdIncludingDeleted(row.getPrizeId());
        PrizeCategoryEntity category = prize == null ? null : categories.getByCode(prize.getCategoryCode());
        try {
            fulfillment.start(row, prize, category);
        } catch (RetryableGrantException ex) {
            failClaim(row, now);
            throw ex;
        }
        row.setStatus(GrantRecordStatuses.GRANTED);
        row.setClaimedAt(now);
        row.setGrantedAt(now);
        row.setUpdatedAt(now);
        int granted = grants.updateIfStatus(row, GrantRecordStatuses.CLAIMING);
        if (granted != 1) {
            throw new BusinessException(RewardErrorCodes.CLAIM_CONFLICT);
        }
        fulfillment.emitArrived(row);
        fulfillment.armThirdPartyStub(row, prize, category);
        return new ClaimResponse(row.getStatus(), row.getFulfillmentStatus());
    }

    private void failClaim(GrantRecordEntity row, LocalDateTime now) {
        int retry = row.getRetryCount() == null ? 0 : row.getRetryCount();
        retry = retry + 1;
        row.setRetryCount(retry);
        row.setUpdatedAt(now);
        if (retry >= settings.claimRetryMax()) {
            row.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
        } else {
            row.setStatus(GrantRecordStatuses.RETRY_PENDING);
            row.setNextRetryAt(row.getExpireAt() == null ? LocalDateTime.of(9999, 12, 31, 0, 0) : row.getExpireAt());
        }
        grants.updateIfStatus(row, GrantRecordStatuses.CLAIMING);
    }

    private LockAcquire tryLock(long recordId) {
        if (locks == null) {
            return LockAcquire.DEGRADED;
        }
        return locks.tryClaimLock(recordId);
    }

    private void unlock(long recordId, LockAcquire acquired) {
        if (locks == null || acquired != LockAcquire.ACQUIRED) {
            return;
        }
        locks.unlock(LockKeys.rwdClaim(recordId));
    }
}
