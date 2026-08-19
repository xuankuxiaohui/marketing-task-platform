package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigValueTypesTest {

    @Test
    void matchesClosedTypes() {
        assertThat(ConfigValueTypes.valid("STRING")).isTrue();
        assertThat(ConfigValueTypes.matches("NUMBER", "12.5")).isTrue();
        assertThat(ConfigValueTypes.matches("NUMBER", "x")).isFalse();
        assertThat(ConfigValueTypes.matches("BOOL", "true")).isTrue();
        assertThat(ConfigValueTypes.matches("BOOL", "yes")).isFalse();
        assertThat(ConfigValueTypes.matches("JSON", "{\"a\":1}")).isTrue();
        assertThat(ConfigValueTypes.matches("JSON", "{")).isFalse();
        assertThat(ConfigValueTypes.matches("STRING", "any")).isTrue();
    }
}
