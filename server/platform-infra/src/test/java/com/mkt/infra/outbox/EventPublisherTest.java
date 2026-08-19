package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EventPublisherTest {

    private final MemoryOutboxStore store = new MemoryOutboxStore();
    private final EventPublisher publisher = new EventPublisher(store, OutboxProducer.ADMIN);

    @AfterEach
    void clearTx() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void appendRequiresActiveTransaction() {
        assertThatThrownBy(() -> publisher.append(OutboxRoutes.TASK_INSTANCE_START, "task_instance", "1", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    void appendWritesProducer() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        long id = publisher.append(OutboxRoutes.TASK_INSTANCE_START, "task_instance", "9", Map.of("userId", 1));
        OutboxRecord row = store.get(id);
        assertThat(row.producer()).isEqualTo("admin");
        assertThat(row.eventCode()).isEqualTo(OutboxRoutes.TASK_INSTANCE_START);
        assertThat(row.payload()).contains("userId");
    }

    @Test
    void rejectBlankEventCode() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> publisher.append(" ", "t", "1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
