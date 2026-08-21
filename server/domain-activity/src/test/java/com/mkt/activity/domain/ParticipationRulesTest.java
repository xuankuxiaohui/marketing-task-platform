package com.mkt.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticipationRulesTest {

    private static final Instant NOW = Instant.parse("2026-08-20T04:00:00Z");

    @Test
    void allowlistNewUserDailyTotalGlobalRegion() {
        UserAttributes fresh = attrs("110000", Instant.parse("2026-08-18T00:00:00Z"));
        assertThat(ParticipationRules.firstReject(
                        List.of(9L), List.of(), false, 7, null, null, null, 0, 0, 0, List.of(), 8L, fresh, NOW))
                .isEqualTo(HitRules.ALLOWLIST);
        assertThat(ParticipationRules.firstReject(
                        List.of(), List.of(), true, 7, null, null, null, 0, 0, 0, List.of(), 9L, attrs("110000", Instant.parse("2026-08-01T00:00:00Z")), NOW))
                .isEqualTo(HitRules.NEW_USER);
        assertThat(ParticipationRules.firstReject(
                        List.of(), List.of(), false, 7, 1, null, null, 1, 0, 0, List.of(), 9L, fresh, NOW))
                .isEqualTo(HitRules.USER_DAILY);
        assertThat(ParticipationRules.firstReject(
                        List.of(), List.of(), false, 7, null, 2, null, 0, 2, 0, List.of(), 9L, fresh, NOW))
                .isEqualTo(HitRules.USER_TOTAL);
        assertThat(ParticipationRules.firstReject(
                        List.of(), List.of(), false, 7, null, null, 10, 0, 0, 10, List.of(), 9L, fresh, NOW))
                .isEqualTo(HitRules.GLOBAL_DAILY);
        assertThat(ParticipationRules.firstReject(
                        List.of(), List.of(), false, 7, null, null, null, 0, 0, 0, List.of("310000"), 9L, fresh, NOW))
                .isEqualTo(HitRules.REGION);
        assertThat(ParticipationRules.firstReject(
                        List.of(9L), List.of(), true, 7, 2, 5, 10, 0, 0, 0, List.of("110000"), 9L, fresh, NOW))
                .isNull();
    }

    @Test
    void crowdOnlyAllowlistRejectsUnknownUser() {
        UserAttributes fresh = attrs("110000", Instant.parse("2026-08-18T00:00:00Z"));
        assertThat(ParticipationRules.firstReject(
                        List.of(), List.of("vip"), false, 7, null, null, null, 0, 0, 0, List.of(), 9L, fresh, NOW))
                .isEqualTo(HitRules.ALLOWLIST);
    }

    private static UserAttributes attrs(String province, Instant registeredAt) {
        return new UserAttributes(province, "user", null, 1, List.of(), registeredAt, AccountStatus.ACTIVE);
    }
}
