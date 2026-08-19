package com.mkt.risk.domain;

import com.mkt.contract.RiskListType;
import java.time.Instant;

/** Effective list row used by {@link ListDecisionEngine}. */
public record ListEntry(
        RiskDimension dimension,
        RiskListType listType,
        String listValue,
        Instant expireAt,
        boolean denyLogin) {

    public ListEntry {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension is required");
        }
        if (listType == null) {
            throw new IllegalArgumentException("listType is required");
        }
        if (listValue == null || listValue.isBlank()) {
            throw new IllegalArgumentException("listValue is required");
        }
    }

    public boolean effectiveAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now is required");
        }
        return expireAt == null || expireAt.isAfter(now);
    }
}
