package com.mkt.infra.nonce;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;

class NonceStoreTest {

    @Test
    void setnxThenReplayAndRejectWhenDown() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        NonceStore nonces = new NonceStore(store);
        assertThat(nonces.tryConsume("app-1", "n1", 600)).isTrue();
        assertThat(nonces.tryConsume("app-1", "n1", 600)).isFalse();
        assertThat(store.get(NonceStore.key("app-1", "n1"))).isEqualTo("1");
        store.setAvailable(false);
        assertThat(nonces.tryConsume("app-1", "n2", 600)).isFalse();
    }
}
