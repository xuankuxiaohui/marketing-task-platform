package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsernamesTest {

    @Test
    void normalizesAndValidates() {
        assertThat(Usernames.normalize("Admin_1")).isEqualTo("admin_1");
        assertThat(Usernames.valid("ab_1")).isTrue();
        assertThat(Usernames.valid("ab")).isFalse();
        assertThat(Usernames.valid("Admin")).isFalse();
        assertThat(Usernames.requireValid("User_01")).isEqualTo("user_01");
        assertThat(Usernames.requireValid("no")).isNull();
    }
}
