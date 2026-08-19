package com.mkt.infra.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;

class KickReasonListenerTest {

    @Test
    void replacedWritesConcurrentAndKickoutWritesAdmin() {
        KickReasonStore store = new KickReasonStore(new MemoryKeyValueStore());
        KickReasonListener listener = new KickReasonListener(store);
        listener.doReplaced("client", 1L, "t1");
        listener.doKickout("admin", 2L, "t2");
        assertThat(store.readAndDelete("client", "t1")).contains(KickReason.CONCURRENT);
        assertThat(store.readAndDelete("admin", "t2")).contains(KickReason.ADMIN);
    }
}
