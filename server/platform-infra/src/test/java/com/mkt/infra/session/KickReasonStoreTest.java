package com.mkt.infra.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;

class KickReasonStoreTest {

    @Test
    void writeReadOnceDeletes() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        KickReasonStore reasons = new KickReasonStore(store);
        reasons.write("client", "tok-1", KickReason.CONCURRENT);
        assertThat(store.get(KickReasonStore.key("client", "tok-1")))
                .isEqualTo("auth.session.kicked-concurrent");
        assertThat(reasons.readAndDelete("client", "tok-1")).contains(KickReason.CONCURRENT);
        assertThat(reasons.readAndDelete("client", "tok-1")).isEmpty();
        reasons.write("admin", "tok-2", KickReason.ADMIN);
        assertThat(reasons.readAndDelete("admin", "tok-2").orElseThrow().code())
                .isEqualTo("auth.session.kicked-admin");
    }
}
