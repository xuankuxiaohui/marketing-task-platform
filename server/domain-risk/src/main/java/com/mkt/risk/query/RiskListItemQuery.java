package com.mkt.risk.query;

import com.mkt.contract.RiskListType;
import com.mkt.kernel.PageQuery;
import com.mkt.risk.domain.RiskDimension;
import java.time.Instant;

public record RiskListItemQuery(
        RiskDimension dimension,
        RiskListType listType,
        String value,
        Instant from,
        Instant to,
        PageQuery page) {

    public RiskListItemQuery {
        page = page == null ? PageQuery.of(null, null) : page;
    }
}
