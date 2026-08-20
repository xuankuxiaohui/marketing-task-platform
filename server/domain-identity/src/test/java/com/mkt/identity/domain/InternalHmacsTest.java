package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class InternalHmacsTest {

    @Test
    void emptyBodySha256AndLowerHexHmac() {
        String stringToSign = InternalHmacs.stringToSign("post", "/internal/task/callback", "1", "n", new byte[0]);
        assertThat(stringToSign)
                .isEqualTo(
                        "POST\n/internal/task/callback\n1\nn\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        String hex = InternalHmacs.signHex("secret", stringToSign);
        assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(InternalHmacs.equalsConstantTime(hex, hex)).isTrue();
        assertThat(InternalHmacs.equalsConstantTime(hex, "0".repeat(64))).isFalse();
        assertThat(InternalHmacs.equalsConstantTime(hex, "zzzz")).isFalse();
        assertThat(InternalHmacs.decodeHex("gg")).isNull();
    }

    @Test
    void signedPathIncludesQueryAndOmitsTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/task/callback");
        request.setRequestURI("/internal/task/callback");
        request.setQueryString("x=1");
        request.addHeader("X-Trace-Id", "trace-should-not-sign");
        assertThat(InternalHmacs.signedPath(request)).isEqualTo("/internal/task/callback?x=1");
        request.setQueryString(null);
        assertThat(InternalHmacs.signedPath(request)).isEqualTo("/internal/task/callback");
    }
}
