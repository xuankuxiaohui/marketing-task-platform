package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.response.PrizeImpactResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Enable and disable entries extracted from {@link PrizeAppService} (R33-2).
 * Move-only: enable / disable semantics unchanged. {@code require} is a lifecycle-side copy;
 * the facade keeps the shared require used by get / delete / replenish / stockLogs.
 */
@Component
public class PrizeLifecycle {

    private final PrizeStore prizes;
    private final SnapshotPrizeLookup snapshots;
    private final Clock clock;
    private final PrizeValidate validate;

    @Autowired
    public PrizeLifecycle(PrizeStore prizes, SnapshotPrizeLookup snapshots, Clock clock, PrizeValidate validate) {
        this.prizes = prizes;
        this.snapshots = snapshots;
        this.clock = clock;
        this.validate = validate;
    }

    PrizeImpactResponse disable(long id, PrizeConfirmCommand command) {
        PrizeEntity existing = require(id);
        if (!PrizeStatuses.ENABLED.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅启用态可停用");
        }
        int affected = snapshots.affectedPublishedTaskCount(id);
        int inFlight = snapshots.inFlightInstanceCount(id);
        if (!Boolean.TRUE.equals(command.confirm())) {
            return new PrizeImpactResponse(false, affected, inFlight);
        }
        existing.setStatus(PrizeStatuses.DISABLED);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
        return new PrizeImpactResponse(true, affected, inFlight);
    }

    PrizeImpactResponse enable(long id, PrizeConfirmCommand command) {
        PrizeEntity existing = require(id);
        if (PrizeStatuses.ENABLED.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "奖品已启用");
        }
        if (PrizeStatuses.DRAFT.equals(existing.getStatus())) {
            validate.requireEnabledCategory(existing.getCategoryCode());
            existing.setStatus(PrizeStatuses.ENABLED);
            existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            prizes.update(existing);
            return new PrizeImpactResponse(true, 0, 0);
        }
        if (!PrizeStatuses.DISABLED.equals(existing.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "状态流转非法");
        }
        validate.requireEnabledCategory(existing.getCategoryCode());
        int affected = snapshots.affectedPublishedTaskCount(id);
        int inFlight = snapshots.inFlightInstanceCount(id);
        if (!Boolean.TRUE.equals(command.confirm())) {
            return new PrizeImpactResponse(false, affected, inFlight);
        }
        existing.setStatus(PrizeStatuses.ENABLED);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        prizes.update(existing);
        return new PrizeImpactResponse(true, affected, inFlight);
    }

    private PrizeEntity require(long id) {
        PrizeEntity existing = prizes.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }
}
