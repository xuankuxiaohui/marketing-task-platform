package com.mkt.risk.support;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.ListDecisionEngine;
import com.mkt.risk.domain.ListEntry;
import com.mkt.risk.domain.ListSegmentOutcome;
import com.mkt.risk.domain.RiskDimension;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Redis-backed list lookup for admin and later RiskCheckPort / task freeze (R25.4). */
@Component
public class ListLookup {

    private final RiskListProjection projection;
    private final Clock clock;

    public ListLookup(RiskListProjection projection, Clock clock) {
        this.projection = projection;
        this.clock = clock;
    }

    public ListDecision decide(RiskScene scene, RiskSubject subject) {
        return evaluate(scene, subject).decision();
    }

    public ListSegmentOutcome evaluate(RiskScene scene, RiskSubject subject) {
        RiskSubject normalized = canonicalize(subject);
        return ListDecisionEngine.evaluate(scene, normalized, snapshot(normalized), clock.instant());
    }

    private static RiskSubject canonicalize(RiskSubject subject) {
        String ip = RiskListImportParser.normalizeOrNull(RiskDimension.IP, subject.ip());
        String device = subject.deviceId() == null
                ? null
                : RiskListImportParser.normalizeOrNull(RiskDimension.DEVICE, subject.deviceId());
        return new RiskSubject(
                subject.userId(),
                ip == null ? subject.ip() : ip,
                device,
                subject.elapsedSeconds(),
                subject.simulated());
    }

    public ListEntry find(RiskDimension dimension, RiskListType listType, String listValue) {
        return projection.lookup(dimension, listType, listValue);
    }

    private List<ListEntry> snapshot(RiskSubject subject) {
        List<RiskListProjection.LookupKey> keys = new ArrayList<>(6);
        if (subject.userId() != null) {
            addKey(keys, RiskDimension.USER, RiskListType.BLACK, String.valueOf(subject.userId()));
            addKey(keys, RiskDimension.USER, RiskListType.WHITE, String.valueOf(subject.userId()));
        }
        addKey(keys, RiskDimension.IP, RiskListType.BLACK, subject.ip());
        addKey(keys, RiskDimension.IP, RiskListType.WHITE, subject.ip());
        if (subject.deviceId() != null) {
            addKey(keys, RiskDimension.DEVICE, RiskListType.BLACK, subject.deviceId());
            addKey(keys, RiskDimension.DEVICE, RiskListType.WHITE, subject.deviceId());
        }
        return projection.lookupMany(keys);
    }

    private static void addKey(
            List<RiskListProjection.LookupKey> keys,
            RiskDimension dimension,
            RiskListType type,
            String value) {
        String key = dimension == RiskDimension.USER
                ? value
                : RiskListImportParser.normalizeOrNull(dimension, value);
        if (key == null || key.isBlank()) {
            return;
        }
        keys.add(new RiskListProjection.LookupKey(dimension, type, key));
    }
}
