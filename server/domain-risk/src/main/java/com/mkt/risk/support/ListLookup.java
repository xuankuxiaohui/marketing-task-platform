package com.mkt.risk.support;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.ListDecisionEngine;
import com.mkt.risk.domain.ListEntry;
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
        RiskSubject normalized = canonicalize(subject);
        return ListDecisionEngine.decide(scene, normalized, snapshot(normalized), clock.instant());
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
                subject.elapsedSeconds());
    }

    public ListEntry find(RiskDimension dimension, RiskListType listType, String listValue) {
        return projection.lookup(dimension, listType, listValue);
    }

    private List<ListEntry> snapshot(RiskSubject subject) {
        List<ListEntry> entries = new ArrayList<>(6);
        if (subject.userId() != null) {
            add(entries, RiskDimension.USER, RiskListType.BLACK, String.valueOf(subject.userId()));
            add(entries, RiskDimension.USER, RiskListType.WHITE, String.valueOf(subject.userId()));
        }
        add(entries, RiskDimension.IP, RiskListType.BLACK, subject.ip());
        add(entries, RiskDimension.IP, RiskListType.WHITE, subject.ip());
        if (subject.deviceId() != null) {
            add(entries, RiskDimension.DEVICE, RiskListType.BLACK, subject.deviceId());
            add(entries, RiskDimension.DEVICE, RiskListType.WHITE, subject.deviceId());
        }
        return entries;
    }

    private void add(List<ListEntry> entries, RiskDimension dimension, RiskListType type, String value) {
        String key = dimension == RiskDimension.USER
                ? value
                : RiskListImportParser.normalizeOrNull(dimension, value);
        if (key == null || key.isBlank()) {
            return;
        }
        ListEntry found = projection.lookup(dimension, type, key);
        if (found != null) {
            entries.add(found);
        }
    }
}
