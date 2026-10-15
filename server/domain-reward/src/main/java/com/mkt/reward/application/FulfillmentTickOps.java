package com.mkt.reward.application;

import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.support.RewardRuntimeSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fulfillment tick helpers extracted from {@link FulfillmentService} (R13-4).
 * Move-only: retryDue / timeoutDue / crossDayPending semantics unchanged.
 */
@Component
public class FulfillmentTickOps {

    static final int BATCH = 100;

    private final GrantRecordStore grants;
    private final FulfillmentLookups lookups;
    private final FulfillmentSettleOps settleOps;
    private final FulfillmentDispatch dispatch;
    private final RewardRuntimeSettings settings;
    private final Clock clock;

    @Autowired
    public FulfillmentTickOps(
            GrantRecordStore grants,
            FulfillmentLookups lookups,
            FulfillmentSettleOps settleOps,
            FulfillmentDispatch dispatch,
            RewardRuntimeSettings settings,
            Clock clock) {
        this.grants = grants;
        this.lookups = lookups;
        this.settleOps = settleOps;
        this.dispatch = dispatch;
        this.settings = settings == null ? new RewardRuntimeSettings() : settings;
        this.clock = clock;
    }

    int retryDue(LocalDateTime now) {
        int ran = 0;
        for (GrantRecordEntity row : grants.listFulfillRetryDue(now, BATCH)) {
            PrizeEntity prize = lookups.prizeOf(row);
            PrizeCategoryEntity category = lookups.categoryOf(prize);
            if (prize != null) {
                dispatch.armThirdPartyStub(row, prize, category);
            }
            ran++;
        }
        return ran;
    }

    int timeoutDue(LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(settings.sendingTimeoutHours());
        int ran = 0;
        for (GrantRecordEntity row : grants.listSendingTimeout(cutoff, BATCH)) {
            settleOps.failTimeout(row, now);
            ran++;
        }
        return ran;
    }

    int crossDayPending(LocalDateTime now) {
        LocalDateTime dayStart = com.mkt.reward.domain.ClaimWindows.dailyStartUtc(clock);
        int ran = 0;
        for (GrantRecordEntity row : grants.listCrossDaySending(dayStart, BATCH)) {
            if (lookups.reconRequired(row)) {
                grants.markReconPending(row.getId(), now);
                ran++;
            }
        }
        return ran;
    }
}
