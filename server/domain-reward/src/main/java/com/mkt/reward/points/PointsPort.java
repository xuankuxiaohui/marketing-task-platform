package com.mkt.reward.points;

import java.time.Instant;

/**
 * In-module points ledger (design §2.2.3). Not a cross-domain port.
 */
public interface PointsPort {

    long earn(long userId, int points, Instant expireAt, String sourceType, String sourceId);

    long consume(long userId, int points, String sourceType, String sourceId, String remark);
}
