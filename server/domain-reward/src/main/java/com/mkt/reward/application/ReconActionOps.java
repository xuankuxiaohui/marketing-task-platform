package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.entity.ReconItemEntity;
import com.mkt.reward.response.ManualGrantResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Action helpers extracted from {@link ReconAppService} (R7-3).
 * Move-only: doManualGrant / doLedgerOnly semantics unchanged.
 */
@Component
public class ReconActionOps {

    private final GrantRecordStore grants;
    private final PrizeStore prizes;
    private final GrantAppService grantApp;
    private final Clock clock;

    @Autowired
    public ReconActionOps(GrantRecordStore grants, PrizeStore prizes, GrantAppService grantApp, Clock clock) {
        this.grants = grants;
        this.prizes = prizes;
        this.grantApp = grantApp;
        this.clock = clock;
    }

    String doManualGrant(ReconItemEntity item, GrantRecordEntity original, ReconActionCommand command) {
        if (command.userId() == null || command.prizeId() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        String sourceId = "recon:" + item.getId();
        String reason = command.reason() == null || command.reason().isBlank() ? "对账补发" : command.reason();
        ManualGrantResponse created = grantApp.reconManualGrant(command.userId(), command.prizeId(), reason, sourceId);
        if (original != null) {
            grants.closeAsManual(original.getId(), RewardTime.toUtc(clock.instant()));
        }
        return String.valueOf(created.recordId());
    }

    String doLedgerOnly(ReconItemEntity item, ReconActionCommand command, LocalDateTime now) {
        if (command.userId() == null || command.prizeId() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        PrizeEntity prize = prizes.getById(command.prizeId());
        if (prize == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        GrantRecordEntity row = new GrantRecordEntity();
        row.setPrizeId(prize.getId());
        row.setPrizeCode(prize.getCode());
        row.setCategoryCode(prize.getCategoryCode());
        row.setCostFen(item.getChannelAmountFen() == null ? 0 : item.getChannelAmountFen());
        row.setFaceFen(null);
        row.setReconStatus(GrantRecordStatuses.RECON_MATCHED);
        row.setUserId(command.userId());
        row.setGrantSource("MANUAL_GRANT");
        row.setSourceId("ledger:" + item.getId());
        row.setStatus(GrantRecordStatuses.GRANTED);
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_ARRIVED);
        row.setFulfillmentRef(item.getFulfillmentRef());
        row.setRetryCount(0);
        row.setSimulated(0);
        row.setGrantedAt(now);
        row.setFulfilledAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        grants.insert(row);
        return String.valueOf(row.getId());
    }
}
