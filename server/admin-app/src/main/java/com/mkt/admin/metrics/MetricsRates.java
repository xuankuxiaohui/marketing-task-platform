package com.mkt.admin.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversion / CTR; denominator 0 → null (R23.1 dash). */
public final class MetricsRates {

    private MetricsRates() {}

    public static Double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
