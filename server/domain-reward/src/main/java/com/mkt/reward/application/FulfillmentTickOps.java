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
    private final RewardRuntimeSettings settings;
    private final Clock clock;
    private FulfillmentService fulfillment;

    @Autowired
    public FulfillmentTickOps(
            GrantRecordStore grants,
            FulfillmentLookups lookups,
            FulfillmentSettleOps settleOps,
            RewardRuntimeSettings settings,
            Clock clock) {
        this.grants = grants;
        this.lookups = lookups;
        this.settleOps = settleOps;
        this.settings = settings == null ? new RewardRuntimeSettings() : settings;
        this.clock = clock;
    }

    /**
     * Wires the owning service after both objects exist. {@code armThirdPartyStub} stays on the service.
     */
    void bind(FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    int retryDue(LocalDateTime now) {
        int ran = 0;
        for (GrantRecordEntity row : grants.listFulfillRetryDue(now, BATCH)) {
            PrizeEntity prize = lookups.prizeOf(row);
            PrizeCategoryEntity category = lookups.categoryOf(prize);
            if (prize != null) {
                fulfillment.armThirdPartyStub(row, prize, category);
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
