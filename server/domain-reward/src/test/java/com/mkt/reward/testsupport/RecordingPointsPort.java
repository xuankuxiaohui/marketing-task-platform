package com.mkt.reward.testsupport;

import com.mkt.reward.points.PointsPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class RecordingPointsPort implements PointsPort {

    public record EarnCall(long userId, int points, Instant expireAt, String sourceType, String sourceId) {}

    private final AtomicLong seq = new AtomicLong(1);
    private final CopyOnWriteArrayList<EarnCall> calls = new CopyOnWriteArrayList<>();
    public RuntimeException failWith;

    @Override
    public long earn(long userId, int points, Instant expireAt, String sourceType, String sourceId) {
        if (failWith != null) {
            throw failWith;
        }
        calls.add(new EarnCall(userId, points, expireAt, sourceType, sourceId));
        return seq.getAndIncrement();
    }

    public List<EarnCall> calls() {
        return new ArrayList<>(calls);
    }
}
