package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.ListDecisionEngine;
import com.mkt.risk.domain.ListEntry;
import com.mkt.risk.domain.RiskDimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R25.1: list decision is deterministic; black wins over white; expired rows do not intercept.
 */
class ListDecisionPropertyTest {

    private static final Logger log = LoggerFactory.getLogger(ListDecisionPropertyTest.class);
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final long USER_ID = 42L;
    private static final String IP = "10.0.0.8";
    private static final String DEVICE = "dev-42";

    @BeforeTry
    void logSeedHint() {
        log.debug("ListDecisionPropertyTest try at {}", NOW);
    }

    @Property(tries = 200)
    void decisionMatchesBlackFirstAndIsDeterministic(
            @ForAll("entrySets") List<ListEntry> entries, @ForAll RiskScene scene) {
        RiskSubject subject = new RiskSubject(USER_ID, IP, DEVICE, null);
        ListDecision first = ListDecisionEngine.decide(scene, subject, entries, NOW);
        ListDecision second = ListDecisionEngine.decide(scene, subject, entries, NOW);
        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo(expected(scene, entries));
        if (hasEffectiveBlackAndWhite(entries, scene)) {
            assertThat(first).isEqualTo(ListDecision.REJECT);
        }
        if (first == ListDecision.SKIP_RULES) {
            assertThat(first).isNotEqualTo(ListDecision.REJECT);
        }
        if (onlyForeignOrExpired(entries)) {
            assertThat(first).isNotEqualTo(ListDecision.REJECT);
        }
    }

    @Provide
    Arbitrary<List<ListEntry>> entrySets() {
        return entry().list().ofMinSize(0).ofMaxSize(8);
    }

    private Arbitrary<ListEntry> entry() {
        Arbitrary<RiskDimension> dims = Arbitraries.of(RiskDimension.values());
        Arbitrary<RiskListType> types = Arbitraries.of(RiskListType.values());
        Arbitrary<ExpiryKind> expiry = Arbitraries.of(ExpiryKind.values());
        Arbitrary<Boolean> deny = Arbitraries.of(true, false);
        Arbitrary<Boolean> otherSubject = Arbitraries.of(true, false);
        return Combinators.combine(dims, types, expiry, deny, otherSubject)
                .as((dim, type, kind, denyLogin, other) -> {
            String value = switch (dim) {
                case USER -> other ? "99" : String.valueOf(USER_ID);
                case IP -> other ? "10.9.9.9" : IP;
                case DEVICE -> other ? "other-dev" : DEVICE;
            };
            Instant expireAt = switch (kind) {
                case PERMANENT -> null;
                case FUTURE -> NOW.plusSeconds(3600);
                case EXPIRED -> NOW.minusSeconds(60);
            };
            boolean denyFlag = dim == RiskDimension.USER && type == RiskListType.BLACK && denyLogin;
            return new ListEntry(dim, type, value, expireAt, denyFlag);
        });
    }

    private static ListDecision expected(RiskScene scene, List<ListEntry> entries) {
        List<ListEntry> live = new ArrayList<>();
        for (ListEntry e : entries) {
            if (e.effectiveAt(NOW)) {
                live.add(e);
            }
        }
        boolean userBlack = contains(live, RiskDimension.USER, RiskListType.BLACK, String.valueOf(USER_ID));
        boolean userDeny = live.stream()
                .anyMatch(e -> e.dimension() == RiskDimension.USER
                        && e.listType() == RiskListType.BLACK
                        && e.listValue().equals(String.valueOf(USER_ID))
                        && e.denyLogin());
        boolean ipBlack = contains(live, RiskDimension.IP, RiskListType.BLACK, IP);
        boolean deviceBlack = contains(live, RiskDimension.DEVICE, RiskListType.BLACK, DEVICE);
        boolean userWhite = contains(live, RiskDimension.USER, RiskListType.WHITE, String.valueOf(USER_ID));
        if (userBlack && (scene == RiskScene.CLAIM || scene == RiskScene.GRANT)) {
            return ListDecision.REJECT;
        }
        if (userDeny && scene == RiskScene.LOGIN) {
            return ListDecision.REJECT;
        }
        if (ipBlack && (scene == RiskScene.REGISTER || scene == RiskScene.LOGIN)) {
            return ListDecision.REJECT;
        }
        if (deviceBlack && (scene == RiskScene.REGISTER || scene == RiskScene.LOGIN)) {
            return ListDecision.REJECT;
        }
        if (userWhite) {
            return ListDecision.SKIP_RULES;
        }
        return ListDecision.PASS;
    }

    private static boolean hasEffectiveBlackAndWhite(List<ListEntry> entries, RiskScene scene) {
        boolean blackApplies = switch (scene) {
            case CLAIM, GRANT -> containsEffective(entries, RiskDimension.USER, RiskListType.BLACK);
            case LOGIN -> containsEffective(entries, RiskDimension.IP, RiskListType.BLACK)
                    || containsEffective(entries, RiskDimension.DEVICE, RiskListType.BLACK)
                    || entries.stream()
                            .anyMatch(e -> e.effectiveAt(NOW)
                                    && e.dimension() == RiskDimension.USER
                                    && e.listType() == RiskListType.BLACK
                                    && e.listValue().equals(String.valueOf(USER_ID))
                                    && e.denyLogin());
            case REGISTER -> containsEffective(entries, RiskDimension.IP, RiskListType.BLACK)
                    || containsEffective(entries, RiskDimension.DEVICE, RiskListType.BLACK);
        };
        boolean white = containsEffective(entries, RiskDimension.USER, RiskListType.WHITE);
        return blackApplies && white;
    }

    private static boolean containsEffective(List<ListEntry> entries, RiskDimension dim, RiskListType type) {
        String value = switch (dim) {
            case USER -> String.valueOf(USER_ID);
            case IP -> IP;
            case DEVICE -> DEVICE;
        };
        return entries.stream().anyMatch(e -> e.effectiveAt(NOW)
                && e.dimension() == dim
                && e.listType() == type
                && e.listValue().equals(value));
    }

    private static boolean onlyForeignOrExpired(List<ListEntry> entries) {
        return entries.stream().allMatch(e -> !e.effectiveAt(NOW) || !matchesSubject(e));
    }

    private static boolean matchesSubject(ListEntry e) {
        return switch (e.dimension()) {
            case USER -> e.listValue().equals(String.valueOf(USER_ID));
            case IP -> e.listValue().equalsIgnoreCase(IP);
            case DEVICE -> e.listValue().equalsIgnoreCase(DEVICE);
        };
    }

    private static boolean contains(List<ListEntry> live, RiskDimension dim, RiskListType type, String value) {
        return live.stream()
                .anyMatch(e -> e.dimension() == dim && e.listType() == type && e.listValue().equals(value));
    }

    private enum ExpiryKind {
        PERMANENT,
        FUTURE,
        EXPIRED
    }
}
