package com.mkt.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Append then rollback leaves no outbox row (R28.3 / R10.3). Requires Docker; leave for CI. */
@Testcontainers
class OutboxRollbackIT {

    @Container
    static final MySQLContainer<?> MYSQL = OutboxITSupport.mysql();

    @Test
    void rollbackRemovesAppendedRow() {
        JdbcTemplate jdbc = OutboxITSupport.migrate(MYSQL);
        JdbcOutboxStore store = new JdbcOutboxStore(jdbc);
        EventPublisher publisher = new EventPublisher(store, OutboxProducer.ADMIN);
        DataSourceTransactionManager txm = OutboxITSupport.tx(jdbc);
        TransactionTemplate tx = new TransactionTemplate(txm);

        tx.executeWithoutResult(status -> {
            publisher.append(OutboxRoutes.TASK_INSTANCE_START, "task_instance", "1", Map.of("userId", 1));
            assertThat(store.countPending("admin")).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(store.countPending("admin")).isZero();
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM sys_outbox", Integer.class);
        assertThat(rows).isZero();
    }
}
