package com.mkt.risk.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;

class RiskCntWindowTest {

    @Test
    void uniqueMembersDoNotCollideAcrossInstances() {
        RiskCntWindow a = new RiskCntWindow(new MemoryKeyValueStore());
        RiskCntWindow b = new RiskCntWindow(new MemoryKeyValueStore());
        long now = 1_724_000_000_000L;
        assertThat(a.uniqueMember(now)).isNotEqualTo(b.uniqueMember(now));
        assertThat(a.uniqueMember(now)).startsWith(now + ":");
    }
}
