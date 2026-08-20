package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DictValuesTest {

    @Test
    void acceptsCharsetAndRejectsSpaces() {
        assertThat(DictValues.normalizeOrNull("GD")).isEqualTo("GD");
        assertThat(DictValues.normalizeOrNull("user_role-1.x")).isEqualTo("user_role-1.x");
        assertThat(DictValues.normalizeOrNull("bad value")).isNull();
        assertThat(DictValues.normalizeOrNull("")).isNull();
    }
}
