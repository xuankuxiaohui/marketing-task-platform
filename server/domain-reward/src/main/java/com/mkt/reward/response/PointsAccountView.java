package com.mkt.reward.response;

import java.time.Instant;

public record PointsAccountView(long id, long userId, long balance, Instant createdAt, Instant updatedAt) {}
