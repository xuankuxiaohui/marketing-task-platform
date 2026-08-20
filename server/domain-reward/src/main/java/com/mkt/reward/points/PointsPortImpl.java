package com.mkt.reward.points;

import com.mkt.reward.application.PointsAppService;
import java.time.Instant;

/** Real ledger behind {@link PointsPort} (task 35 / design §5.8). */
public class PointsPortImpl implements PointsPort {

    private final PointsAppService points;

    public PointsPortImpl(PointsAppService points) {
        this.points = points;
    }

    @Override
    public long earn(long userId, int pointsAmount, Instant expireAt, String sourceType, String sourceId) {
        return points.earn(userId, pointsAmount, expireAt, sourceType, sourceId);
    }
}
