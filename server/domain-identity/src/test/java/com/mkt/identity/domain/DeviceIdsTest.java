package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeviceIdsTest {

    @Test
    void acceptsUuidV4Only() {
        assertThat(DeviceIds.normalizeOrNull("550e8400-e29b-41d4-a716-446655440000")).isNotBlank();
        assertThat(DeviceIds.normalizeOrNull("not-a-uuid")).isNull();
        assertThat(DeviceIds.normalizeOrNull("")).isNull();
        assertThat(DeviceIds.normalizeOrNull(null)).isNull();
    }
}
