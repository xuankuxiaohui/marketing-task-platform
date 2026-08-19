package com.mkt.infra.lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LockKeysTest {

    @Test
    void familiesMatchSection310() {
        assertThat(LockKeys.lock("scope")).isEqualTo("lock:scope");
        assertThat(LockKeys.sched("publish-scan")).isEqualTo("sched:publish-scan");
        assertThat(LockKeys.rwdClaim(9L)).isEqualTo("lock:rwd-claim:9");
        assertThat(LockKeys.outboxRelay("admin")).isEqualTo("outbox:relay:admin");
    }
}
