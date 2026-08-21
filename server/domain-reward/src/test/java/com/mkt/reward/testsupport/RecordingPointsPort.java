package com.mkt.reward.testsupport;

import com.mkt.reward.points.PointsPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class RecordingPointsPort implements PointsPort {

    public record EarnCall(
            long userId, int points, Instant expireAt, String sourceType, String sourceId, boolean simulated) {
        public EarnCall(long userId, int points, Instant expireAt, String sourceType, String sourceId) {
            this(userId, points, expireAt, sourceType, sourceId, false);
        }
    }

    public record ConsumeCall(long userId, int points, String sourceType, String sourceId, String remark) {}

    private final AtomicLong seq = new AtomicLong(1);
    private final CopyOnWriteArrayList<EarnCall> calls = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ConsumeCall> consumeCalls = new CopyOnWriteArrayList<>();
    public RuntimeException failWith;

    @Override
    public long earn(long userId, int points, Instant expireAt, String sourceType, String sourceId) {
        return earn(userId, points, expireAt, sourceType, sourceId, false);
    }

    @Override
    public long earn(
            long userId, int points, Instant expireAt, String sourceType, String sourceId, boolean simulated) {
        if (failWith != null) {
            throw failWith;
        }
        calls.add(new EarnCall(userId, points, expireAt, sourceType, sourceId, simulated));
        return seq.getAndIncrement();
    }

    @Override
    public long consume(long userId, int points, String sourceType, String sourceId, String remark) {
        if (failWith != null) {
            throw failWith;
        }
        consumeCalls.add(new ConsumeCall(userId, points, sourceType, sourceId, remark));
        return seq.getAndIncrement();
    }

    public List<EarnCall> calls() {
        return new ArrayList<>(calls);
    }

    public List<ConsumeCall> consumeCalls() {
        return new ArrayList<>(consumeCalls);
    }
}
