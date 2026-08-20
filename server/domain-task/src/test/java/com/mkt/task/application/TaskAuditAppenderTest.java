package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.response.PublishCheckError;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

class TaskAuditAppenderTest {

    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private final TaskAuditAppender appender =
            new TaskAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN));

    @AfterEach
    void clearTx() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void schedulePublishFailureSerializesCheckErrorsIntoSummaryAndError() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        appender.schedulePublishFailure(
                9L,
                "due_bad",
                "发布校验失败",
                List.of(new PublishCheckError("reachability", "存在不可达或死锁步骤")));

        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("action").asString()).isEqualTo("schedule-publish-failure");
        assertThat(payload.get("result").asString()).isEqualTo("FAILURE");
        assertThat(payload.get("errorMessage").asString()).contains("reachability");
        assertThat(payload.get("errorMessage").asString()).contains("存在不可达或死锁步骤");
        assertThat(payload.get("errorMessage").asString()).isNotEqualTo("发布校验失败");

        JsonNode summary = JsonUtil.readTree(payload.get("requestSummary").asString());
        assertThat(summary.get("taskId").asLong()).isEqualTo(9L);
        assertThat(summary.get("code").asString()).isEqualTo("due_bad");
        assertThat(summary.get("reason").asString()).isEqualTo("发布校验失败");
        assertThat(summary.get("checkErrors").isArray()).isTrue();
        assertThat(summary.get("checkErrors").get(0).get("item").asString()).isEqualTo("reachability");
        assertThat(summary.get("checkErrors").get(0).get("reason").asString()).isEqualTo("存在不可达或死锁步骤");
    }

    @Test
    void schedulePublishFailureWithoutCheckErrorsKeepsMessage() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        appender.schedulePublishFailure(3L, "due_ok", "lock timeout");

        JsonNode payload = JsonUtil.readTree(outbox.all().get(0).payload());
        assertThat(payload.get("errorMessage").asString()).isEqualTo("lock timeout");
        JsonNode summary = JsonUtil.readTree(payload.get("requestSummary").asString());
        assertThat(summary.get("reason").asString()).isEqualTo("lock timeout");
        assertThat(summary.get("checkErrors")).isNull();
    }
}
