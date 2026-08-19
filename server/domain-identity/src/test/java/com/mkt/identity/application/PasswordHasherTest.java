package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void bcryptCostAtLeast12AndDoesNotMatchBlank() {
        String hash = hasher.hash("Abcdef12!x");
        assertThat(hash).startsWith("$2a$12$");
        assertThat(hasher.matches("Abcdef12!x", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
        assertThat(hasher.matches("Abcdef12!x", "")).isFalse();
    }
}
