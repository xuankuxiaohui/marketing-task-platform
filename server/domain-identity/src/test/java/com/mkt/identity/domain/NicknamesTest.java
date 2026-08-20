package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknamesTest {

    @Test
    void acceptsCjkLatinDigitUnderscore() {
        assertThat(Nicknames.valid("用户_A1")).isTrue();
        assertThat(Nicknames.valid("a")).isTrue();
        assertThat(Nicknames.valid(" bad")).isFalse();
        assertThat(Nicknames.valid("bad nick")).isFalse();
        assertThat(Nicknames.valid("")).isFalse();
        assertThat(Nicknames.valid("x".repeat(31))).isFalse();
        assertThat(Nicknames.normalize("  昵称 ")).isEqualTo("昵称");
    }
}
