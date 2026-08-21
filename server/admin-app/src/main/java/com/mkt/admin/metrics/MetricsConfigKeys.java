package com.mkt.admin.metrics;

/** Appendix A keys used by R23 aggregation. */
public final class MetricsConfigKeys {

    public static final String MAX_DELAY_MINUTES = "metrics.aggregate.max-delay-minutes";
    public static final int DEFAULT_MAX_DELAY_MINUTES = 5;
    public static final String RETENTION_DAYS = "retention.metrics-days";
    public static final int DEFAULT_RETENTION_DAYS = 365;
    public static final int LOOKBACK_DAYS = 7;
    public static final int CLEAN_BATCH = 5000;

    private MetricsConfigKeys() {}
}
