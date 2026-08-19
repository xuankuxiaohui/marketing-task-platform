package com.mkt.portal.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamespacePrefixesTest {

    @Test
    void allowsApiInternalAndActuator() {
        assertThat(NamespacePrefixes.allows("/api")).isTrue();
        assertThat(NamespacePrefixes.allows("/api/common/task")).isTrue();
        assertThat(NamespacePrefixes.allows("/internal")).isTrue();
        assertThat(NamespacePrefixes.allows("/internal/task/callback")).isTrue();
        assertThat(NamespacePrefixes.allows("/actuator/health")).isTrue();
    }

    @Test
    void rejectsAdminAndRoot() {
        assertThat(NamespacePrefixes.allows("/admin")).isFalse();
        assertThat(NamespacePrefixes.allows("/administrator")).isFalse();
        assertThat(NamespacePrefixes.allows("/")).isFalse();
        assertThat(NamespacePrefixes.allows(null)).isFalse();
    }
}
