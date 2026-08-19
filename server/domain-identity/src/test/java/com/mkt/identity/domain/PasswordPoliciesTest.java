package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPoliciesTest {

    @Test
    void adminRequiresTenWithClasses() {
        assertThat(PasswordPolicies.adminSatisfied("Abcdef1!xy")).isTrue();
        assertThat(PasswordPolicies.adminSatisfied("abcdef1!xy")).isFalse();
        assertThat(PasswordPolicies.adminSatisfied("ABCDEF1!XY")).isFalse();
        assertThat(PasswordPolicies.adminSatisfied("Abcdefghij")).isFalse();
        assertThat(PasswordPolicies.adminSatisfied("Ab1!short")).isFalse();
    }

    @Test
    void portalRequiresEightLetterAndDigit() {
        assertThat(PasswordPolicies.portalSatisfied("abcdefg1")).isTrue();
        assertThat(PasswordPolicies.portalSatisfied("abcdefgh")).isFalse();
        assertThat(PasswordPolicies.portalSatisfied("12345678")).isFalse();
        assertThat(PasswordPolicies.portalSatisfied("abc1")).isFalse();
    }
}
