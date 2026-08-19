package com.mkt.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MemoryKeyValueStoreTtlTest {

    @Test
    void ttlSecondsWithoutExpireIsMinusOne() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        store.set("a", "b");
        assertThat(store.ttlSeconds("a")).isEqualTo(-1L);
        assertThat(store.ttlSeconds("missing")).isEqualTo(-2L);
        store.set("c", "d", Duration.ofSeconds(30));
        assertThat(store.ttlSeconds("c")).isGreaterThan(0L);
    }
}
