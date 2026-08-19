package com.mkt.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryKeyValueStoreGetManyTest {

    @Test
    void getManyReturnsOnlyPresentKeys() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        store.set("a", "1");
        store.set("c", "3");
        assertThat(store.getMany(List.of("a", "b", "c"))).containsOnly(entry("a", "1"), entry("c", "3"));
        assertThat(store.getMany(List.of())).isEmpty();
    }
}
