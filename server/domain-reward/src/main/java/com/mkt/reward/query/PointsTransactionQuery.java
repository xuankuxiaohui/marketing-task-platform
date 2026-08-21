package com.mkt.reward.query;

import com.mkt.kernel.PageQuery;
import java.time.Instant;

public record PointsTransactionQuery(Long userId, String type, Instant from, Instant to, PageQuery page) {}
