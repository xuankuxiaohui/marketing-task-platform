package com.mkt.reward.application;

import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.response.SpendResponse;
import com.mkt.reward.response.SpendRowView;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** Spend aggregation (R37.1). simulated=0. */
@Service
public class SpendAppService {

    private final GrantRecordStore grants;

    public SpendAppService(GrantRecordStore grants) {
        this.grants = grants;
    }

    public SpendResponse spend(String categoryCode, Long prizeId, Instant from, Instant to) {
        LocalDateTime fromUtc = from == null ? null : RewardTime.toUtc(from);
        LocalDateTime toUtc = to == null ? null : RewardTime.toUtc(to);
        List<SpendRowView> rows = grants.sumSpend(categoryCode, prizeId, fromUtc, toUtc);
        return new SpendResponse(rows);
    }
}
