package com.mkt.admin.metrics;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;

/** Query grain over daily aggregate rows (R23.5). */
public enum MetricsGrain {
    DAY,
    WEEK,
    MONTH;

    public static MetricsGrain parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAY;
        }
        try {
            return MetricsGrain.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "grain");
        }
    }
}
