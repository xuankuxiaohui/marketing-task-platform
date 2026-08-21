package com.mkt.admin.metrics;

import java.time.Instant;

public record MetricsQuery(Instant from, Instant to, MetricsGrain grain, String dimKey) {}
