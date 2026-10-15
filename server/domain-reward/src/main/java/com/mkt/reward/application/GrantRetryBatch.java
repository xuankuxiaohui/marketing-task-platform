package com.mkt.reward.application;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Grant retry-batch helpers extracted from {@link GrantAppService} (R14-3).
 * Move-only: retryDue / listDueRetry semantics unchanged.
 * {@code retryDue} takes the service so {@code grant} / {@code resume} stay direct calls.
 */
@Component
public class GrantRetryBatch {

    static final int RETRY_BATCH = 100;

    private final GrantRecordStore grants;
    private final Clock clock;

    @Autowired
    public GrantRetryBatch(GrantRecordStore grants, Clock clock) {
        this.grants = grants;
        this.clock = clock;
    }

    List<GrantRecordEntity> listDueRetry() {
        return grants.listDueRetry(RewardTime.toUtc(clock.instant()), RETRY_BATCH);
    }

    int retryDue(GrantAppService service) {
        List<GrantRecordEntity> due = listDueRetry();
        int ran = 0;
        for (GrantRecordEntity row : due) {
            try {
                GrantSource source = GrantSource.valueOf(row.getGrantSource());
                GrantContext ctx = new GrantContext(null, List.of(), null, simulated(row), null);
                GrantResult result = service.grant(row.getPrizeId(), row.getUserId(), source, row.getSourceId(), ctx);
                if (result.status() == GrantStatus.GRANTED || result.status() == GrantStatus.WON) {
                    service.resume(source, row.getSourceId());
                }
                ran++;
            } catch (RuntimeException ex) {
                ran++;
            }
        }
        return ran;
    }

    private static boolean simulated(GrantRecordEntity row) {
        return row.getSimulated() != null && row.getSimulated() == 1;
    }
}
