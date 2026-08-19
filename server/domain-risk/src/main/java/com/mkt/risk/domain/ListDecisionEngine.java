package com.mkt.risk.domain;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Pure list-segment of design §5.9 (black first, expiry filter). Rule chain is task 18.
 */
public final class ListDecisionEngine {

    private ListDecisionEngine() {
    }

    public static ListDecision decide(RiskScene scene, RiskSubject subject, List<ListEntry> entries, Instant now) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(now, "now");
        List<ListEntry> live = entries == null ? List.of() : entries.stream()
                .filter(e -> e.effectiveAt(now))
                .toList();

        if (userBlack(live, subject) && (scene == RiskScene.CLAIM || scene == RiskScene.GRANT)) {
            return ListDecision.REJECT;
        }
        if (userBlackDenyLogin(live, subject) && scene == RiskScene.LOGIN) {
            return ListDecision.REJECT;
        }
        if (matches(live, RiskDimension.IP, RiskListType.BLACK, subject.ip())
                && (scene == RiskScene.REGISTER || scene == RiskScene.LOGIN)) {
            return ListDecision.REJECT;
        }
        if (matches(live, RiskDimension.DEVICE, RiskListType.BLACK, subject.deviceId())
                && (scene == RiskScene.REGISTER || scene == RiskScene.LOGIN)) {
            return ListDecision.REJECT;
        }
        if (userWhite(live, subject)) {
            return ListDecision.SKIP_RULES;
        }
        return ListDecision.PASS;
    }

    private static boolean userBlack(List<ListEntry> live, RiskSubject subject) {
        return subject.userId() != null
                && matches(live, RiskDimension.USER, RiskListType.BLACK, String.valueOf(subject.userId()));
    }

    private static boolean userBlackDenyLogin(List<ListEntry> live, RiskSubject subject) {
        if (subject.userId() == null) {
            return false;
        }
        String value = String.valueOf(subject.userId());
        return live.stream()
                .anyMatch(e -> e.dimension() == RiskDimension.USER
                        && e.listType() == RiskListType.BLACK
                        && e.listValue().equals(value)
                        && e.denyLogin());
    }

    private static boolean userWhite(List<ListEntry> live, RiskSubject subject) {
        return subject.userId() != null
                && matches(live, RiskDimension.USER, RiskListType.WHITE, String.valueOf(subject.userId()));
    }

    private static boolean matches(
            List<ListEntry> live, RiskDimension dimension, RiskListType type, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return live.stream()
                .anyMatch(e -> e.dimension() == dimension
                        && e.listType() == type
                        && valueMatches(dimension, e.listValue(), value));
    }

    private static boolean valueMatches(RiskDimension dimension, String stored, String value) {
        if (dimension == RiskDimension.USER) {
            return stored.equals(value);
        }
        return stored.equalsIgnoreCase(value);
    }
}
