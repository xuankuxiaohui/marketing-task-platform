package com.mkt.reward.points;

import java.time.Instant;

/**
 * In-module points ledger (design §2.2.3). Not a cross-domain port.
 * Task 33 calls {@link #earn} on INSTANT POINTS grants; task 35 supplies the real ledger.
 */
public interface PointsPort {

    long earn(long userId, int points, Instant expireAt, String sourceType, String sourceId);
}
