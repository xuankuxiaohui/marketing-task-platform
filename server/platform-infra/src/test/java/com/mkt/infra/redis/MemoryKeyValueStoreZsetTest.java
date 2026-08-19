package com.mkt.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoryKeyValueStoreZsetTest {

    @Test
    void trimAndCountMatchInclusiveWindow() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        store.zadd("risk:cnt:R-f:IP:1", 100, "a");
        store.zadd("risk:cnt:R-f:IP:1", 150, "b");
        store.zadd("risk:cnt:R-f:IP:1", 200, "c");
        assertThat(store.zremrangeByScore("risk:cnt:R-f:IP:1", Double.NEGATIVE_INFINITY, 149)).isEqualTo(1);
        assertThat(store.zcount("risk:cnt:R-f:IP:1", 150, 200)).isEqualTo(2);
    }
}
