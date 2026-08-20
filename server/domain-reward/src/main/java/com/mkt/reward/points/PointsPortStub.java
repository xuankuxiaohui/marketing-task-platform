package com.mkt.reward.points;

import java.time.Instant;

/** Replaceable stand-in until task 35 implements {@link PointsPort#earn}. */
public class PointsPortStub implements PointsPort {

    @Override
    public long earn(long userId, int points, Instant expireAt, String sourceType, String sourceId) {
        return 0L;
    }
}
