package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsrfTokensTest {

    @Test
    void equalIsConstantTimeAndRejectsNull() {
        String token = CsrfTokens.create();
        assertThat(token).hasSize(32);
        assertThat(CsrfTokens.equal(token, token)).isTrue();
        assertThat(CsrfTokens.equal(token, "other")).isFalse();
        assertThat(CsrfTokens.equal(null, token)).isFalse();
    }
}
