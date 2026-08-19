package com.mkt.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TinyintOneOrTrueTest {

    @Test
    void acceptsBooleanTrueAndNumberOne() {
        assertThat(TinyintFlags.isOneOrTrue(Boolean.TRUE)).isTrue();
        assertThat(TinyintFlags.isOneOrTrue(1)).isTrue();
        assertThat(TinyintFlags.isOneOrTrue((byte) 1)).isTrue();
        assertThat(TinyintFlags.isOneOrTrue(1L)).isTrue();
    }

    @Test
    void rejectsFalseZeroNullAndOtherTypes() {
        assertThat(TinyintFlags.isOneOrTrue(Boolean.FALSE)).isFalse();
        assertThat(TinyintFlags.isOneOrTrue(0)).isFalse();
        assertThat(TinyintFlags.isOneOrTrue(2)).isFalse();
        assertThat(TinyintFlags.isOneOrTrue(null)).isFalse();
        assertThat(TinyintFlags.isOneOrTrue("1")).isFalse();
    }
}
