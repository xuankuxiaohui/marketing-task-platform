package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Delete entry extracted from {@link PrizeAppService} (R33-5).
 * Move-only: delete semantics unchanged. {@code require} delegates to package-visible
 * {@link PrizeReads#require}.
 */
@Component
public class PrizeDelete {

    private final PrizeStore prizes;
    private final SnapshotPrizeLookup snapshots;
    private final Clock clock;
    private final PrizeReads prizeReads;

    @Autowired
    public PrizeDelete(PrizeStore prizes, SnapshotPrizeLookup snapshots, Clock clock, PrizeReads prizeReads) {
        this.prizes = prizes;
        this.snapshots = snapshots;
        this.clock = clock;
        this.prizeReads = prizeReads;
    }

    void delete(long id) {
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

    private PrizeEntity require(long id) {
        return prizeReads.require(id);
    }
}
