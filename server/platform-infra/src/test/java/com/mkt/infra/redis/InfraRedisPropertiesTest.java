package com.mkt.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InfraRedisPropertiesTest {

    @Test
    void neverDefaultsToDbZero() {
        InfraRedisProperties props = new InfraRedisProperties("", 0, null, 0);
        assertThat(props.database()).isEqualTo(2);
        assertThat(props.host()).isEqualTo("127.0.0.1");
        assertThat(props.port()).isEqualTo(6379);
        assertThat(props.address()).isEqualTo("redis://127.0.0.1:6379");
    }

    @Test
    void nonDefaultDatabaseIsPinnedToTwo() {
        assertThat(new InfraRedisProperties("127.0.0.1", 6379, "", 1).database()).isEqualTo(2);
        assertThat(new InfraRedisProperties("127.0.0.1", 6379, "", 2).database()).isEqualTo(2);
    }
}
