package com.mkt.reward.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconPoliciesTest {

    @Test
    void prizeNullInheritsCategory() {
        assertThat(ReconPolicies.effective(null, ReconPolicies.REVIEW)).isEqualTo(ReconPolicies.REVIEW);
        assertThat(ReconPolicies.effective(ReconPolicies.AUTO, ReconPolicies.REVIEW)).isEqualTo(ReconPolicies.AUTO);
    }

    @Test
    void reconRequiredBuiltinsAreThree() {
        assertThat(BuiltinCategories.RECON_REQUIRED)
                .containsExactlyInAnyOrder(
                        BuiltinCategories.ALIPAY_RED,
                        BuiltinCategories.WECHAT_RED,
                        BuiltinCategories.PHONE_CREDIT);
        assertThat(BuiltinCategories.ALL).hasSize(7);
    }
}
