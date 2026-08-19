package com.mkt.risk.query;

import com.mkt.kernel.PageQuery;
import java.time.Instant;

public record RiskHitQuery(
        String ruleCode,
        String hitType,
        String dimensionValue,
        Long userId,
        String actionResult,
        Instant from,
        Instant to,
        PageQuery page) {

    public RiskHitQuery {
        page = page == null ? PageQuery.of(null, null) : page;
    }
}
