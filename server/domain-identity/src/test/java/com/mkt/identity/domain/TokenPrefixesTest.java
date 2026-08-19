package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenPrefixesTest {

    @Test
    void wrapAndUnwrapAreNotInterchangeable() {
        assertThat(TokenPrefixes.unwrapAdmin(TokenPrefixes.wrapAdmin("raw"))).isEqualTo("raw");
        assertThat(TokenPrefixes.unwrapClient(TokenPrefixes.wrapAdmin("raw"))).isNull();
        assertThat(TokenPrefixes.unwrapAdmin(TokenPrefixes.wrapClient("raw"))).isNull();
        assertThat(TokenPrefixes.looksAdmin("admin:x")).isTrue();
        assertThat(TokenPrefixes.looksClient("client:x")).isTrue();
    }
}
