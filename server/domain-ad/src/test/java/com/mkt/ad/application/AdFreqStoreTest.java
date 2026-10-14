package com.mkt.ad.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AdFreqStoreTest {

    @Test
    void countersCooldownAndBlankReads() {
        MemoryKeyValueStore kv = new MemoryKeyValueStore();
        AdFreqStore freq = new AdFreqStore(kv);
        assertThat(freq.current("ad:freq:9:3:20260820")).isZero();
        assertThat(freq.increment("ad:freq:9:3:20260820", Duration.ofHours(1))).isEqualTo(1L);
        assertThat(freq.increment("ad:freq:9:3:20260820", Duration.ofHours(1))).isEqualTo(2L);
        assertThat(freq.current("ad:freq:9:3:20260820")).isEqualTo(2L);
        kv.set("blank", "  ", Duration.ofMinutes(1));
        assertThat(freq.current("blank")).isZero();

        freq.setTo("ad:freq:9:3:20260820", 10L, Duration.ofMinutes(30));
        assertThat(freq.current("ad:freq:9:3:20260820")).isEqualTo(10L);
        assertThat(kv.ttlSeconds("ad:freq:9:3:20260820")).isEqualTo(Duration.ofMinutes(30).toSeconds());

        assertThat(freq.hasCooldown("ad:popup:cd:9")).isFalse();
        assertThat(freq.trySetCooldown("ad:popup:cd:9", Duration.ofSeconds(600))).isTrue();
        assertThat(freq.trySetCooldown("ad:popup:cd:9", Duration.ofSeconds(600))).isFalse();
        assertThat(freq.hasCooldown("ad:popup:cd:9")).isTrue();
    }

    @Test
    void redisErrorsFailClosed() {
        MemoryKeyValueStore kv = new MemoryKeyValueStore();
        kv.set("bad", "nope", Duration.ofSeconds(30));
        AdFreqStore freq = new AdFreqStore(kv);
        assertThat(freq.current("bad")).isEqualTo(Long.MAX_VALUE);

        kv.setAvailable(false);
        assertThat(freq.increment("k", Duration.ofSeconds(1))).isEqualTo(Long.MAX_VALUE);
        assertThat(freq.current("k")).isEqualTo(Long.MAX_VALUE);
        freq.setTo("k", 1L, Duration.ofSeconds(1));
        assertThat(freq.trySetCooldown("cd", Duration.ofSeconds(1))).isFalse();
        assertThat(freq.hasCooldown("cd")).isTrue();
    }
}
