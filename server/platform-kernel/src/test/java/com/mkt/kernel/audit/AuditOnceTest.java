package com.mkt.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditOnceTest {

    @AfterEach
    void tearDown() {
        AuditOnce.clear();
    }

    @Test
    void markIsVisibleUntilCleared() {
        assertThat(AuditOnce.written()).isFalse();
        AuditOnce.mark();
        assertThat(AuditOnce.written()).isTrue();
        AuditOnce.clear();
        assertThat(AuditOnce.written()).isFalse();
    }
}
