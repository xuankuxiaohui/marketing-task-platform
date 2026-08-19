package com.mkt.admin.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamespacePrefixesTest {

    @Test
    void allowsAdminAndActuator() {
        assertThat(NamespacePrefixes.allows("/admin")).isTrue();
        assertThat(NamespacePrefixes.allows("/admin/")).isTrue();
        assertThat(NamespacePrefixes.allows("/admin/auth/login")).isTrue();
        assertThat(NamespacePrefixes.allows("/actuator")).isTrue();
        assertThat(NamespacePrefixes.allows("/actuator/health")).isTrue();
    }

    @Test
    void rejectsForeignAndMalformed() {
        assertThat(NamespacePrefixes.allows("/administrator")).isFalse();
        assertThat(NamespacePrefixes.allows("/api")).isFalse();
        assertThat(NamespacePrefixes.allows("/internal")).isFalse();
        assertThat(NamespacePrefixes.allows("/")).isFalse();
        assertThat(NamespacePrefixes.allows("")).isFalse();
        assertThat(NamespacePrefixes.allows(null)).isFalse();
        assertThat(NamespacePrefixes.allows("admin")).isTrue();
    }
}
