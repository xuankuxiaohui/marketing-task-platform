package com.mkt.task.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.json.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes {@code audit.log} for schedule-publish-failure (design §6.7-1). */
@Component
public class TaskAuditAppender {

    private final EventPublisher eventPublisher;

    public TaskAuditAppender(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void schedulePublishFailure(long taskId, String code, String reason) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", taskId);
        summary.put("code", code);
        summary.put("reason", reason);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", "task");
        payload.put("action", "schedule-publish-failure");
        payload.put("operatorId", null);
        payload.put("operatorName", "system");
        payload.put("result", "FAILURE");
        payload.put("requestSummary", JsonUtil.toJson(summary));
        payload.put("errorMessage", reason);
        eventPublisher.append(EventCodes.AUDIT_LOG, "task_definition", String.valueOf(taskId), payload);
    }
}
