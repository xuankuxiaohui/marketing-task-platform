package com.mkt.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListDecisionEngineTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
    private static final RiskSubject SUBJECT = new RiskSubject(7L, "1.1.1.1", "dev-7", null);

    @Test
    void claimRejectsUserBlackEvenWhenWhiteExists() {
        List<ListEntry> entries = List.of(
                black(RiskDimension.USER, "7", null, false),
                white(RiskDimension.USER, "7"));
        assertThat(ListDecisionEngine.decide(RiskScene.CLAIM, SUBJECT, entries, NOW))
                .isEqualTo(ListDecision.REJECT);
    }

    @Test
    void registerIgnoresUserBlack() {
        List<ListEntry> entries = List.of(black(RiskDimension.USER, "7", null, false));
        assertThat(ListDecisionEngine.decide(RiskScene.REGISTER, SUBJECT, entries, NOW))
                .isEqualTo(ListDecision.PASS);
    }

    @Test
    void loginRejectsOnlyWhenDenyLogin() {
        List<ListEntry> plain = List.of(black(RiskDimension.USER, "7", null, false));
        List<ListEntry> deny = List.of(black(RiskDimension.USER, "7", null, true));
        assertThat(ListDecisionEngine.decide(RiskScene.LOGIN, SUBJECT, plain, NOW)).isEqualTo(ListDecision.PASS);
        assertThat(ListDecisionEngine.decide(RiskScene.LOGIN, SUBJECT, deny, NOW)).isEqualTo(ListDecision.REJECT);
    }

    @Test
    void registerRejectsIpAndDeviceBlack() {
        assertThat(ListDecisionEngine.decide(
                        RiskScene.REGISTER, SUBJECT, List.of(black(RiskDimension.IP, "1.1.1.1", null, false)), NOW))
                .isEqualTo(ListDecision.REJECT);
        assertThat(ListDecisionEngine.decide(
                        RiskScene.REGISTER, SUBJECT, List.of(black(RiskDimension.DEVICE, "dev-7", null, false)), NOW))
                .isEqualTo(ListDecision.REJECT);
    }

    @Test
    void ipAndDeviceMatchIgnoreCase() {
        RiskSubject mixed = new RiskSubject(7L, "2001:DB8::1", "Dev-7", null);
        assertThat(ListDecisionEngine.decide(
                        RiskScene.LOGIN,
                        mixed,
                        List.of(black(RiskDimension.IP, "2001:db8::1", null, false)),
                        NOW))
                .isEqualTo(ListDecision.REJECT);
        assertThat(ListDecisionEngine.decide(
                        RiskScene.LOGIN,
                        mixed,
                        List.of(black(RiskDimension.DEVICE, "dev-7", null, false)),
                        NOW))
                .isEqualTo(ListDecision.REJECT);
    }

    @Test
    void claimIgnoresDeviceBlack() {
        List<ListEntry> entries = List.of(black(RiskDimension.DEVICE, "dev-7", null, false));
        assertThat(ListDecisionEngine.decide(RiskScene.CLAIM, SUBJECT, entries, NOW)).isEqualTo(ListDecision.PASS);
    }

    @Test
    void expiredBlackDoesNotReject() {
        List<ListEntry> entries = List.of(black(RiskDimension.USER, "7", NOW.minusSeconds(1), false));
        assertThat(ListDecisionEngine.decide(RiskScene.CLAIM, SUBJECT, entries, NOW)).isEqualTo(ListDecision.PASS);
    }

    @Test
    void expiredIpBlackDoesNotBlockLogin() {
        Instant expireAt = NOW.minusSeconds(1);
        assertThat(ListDecisionEngine.decide(
                        RiskScene.LOGIN,
                        SUBJECT,
                        List.of(black(RiskDimension.IP, "1.1.1.1", expireAt, false)),
                        NOW))
                .isEqualTo(ListDecision.PASS);
    }

    @Test
    void expiredDenyLoginUserBlackDoesNotBlockLogin() {
        Instant expireAt = NOW.minusSeconds(1);
        assertThat(ListDecisionEngine.decide(
                        RiskScene.LOGIN,
                        SUBJECT,
                        List.of(black(RiskDimension.USER, "7", expireAt, true)),
                        NOW))
                .isEqualTo(ListDecision.PASS);
        assertThat(ListDecisionEngine.decide(
                        RiskScene.LOGIN,
                        SUBJECT,
                        List.of(black(RiskDimension.USER, "7", null, true)),
                        NOW))
                .isEqualTo(ListDecision.REJECT);
    }

    @Test
    void whiteOnlySkipsRules() {
        List<ListEntry> entries = List.of(white(RiskDimension.USER, "7"));
        assertThat(ListDecisionEngine.decide(RiskScene.CLAIM, SUBJECT, entries, NOW))
                .isEqualTo(ListDecision.SKIP_RULES);
    }

    private static ListEntry black(RiskDimension dim, String value, Instant expireAt, boolean denyLogin) {
        return new ListEntry(dim, RiskListType.BLACK, value, expireAt, denyLogin);
    }

    private static ListEntry white(RiskDimension dim, String value) {
        return new ListEntry(dim, RiskListType.WHITE, value, null, false);
    }
}
