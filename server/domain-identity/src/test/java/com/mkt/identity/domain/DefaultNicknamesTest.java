package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultNicknamesTest {

    @Test
    void usesLastSixDigits() {
        assertThat(DefaultNicknames.of(1L)).isEqualTo("用户1");
        assertThat(DefaultNicknames.of(123456L)).isEqualTo("用户123456");
        assertThat(DefaultNicknames.of(9_012_345L)).isEqualTo("用户012345");
    }
}
