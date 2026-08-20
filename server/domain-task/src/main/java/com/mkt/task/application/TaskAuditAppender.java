package com.mkt.task.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.response.PublishCheckError;
import java.util.LinkedHashMap;
import java.util.List;
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
        schedulePublishFailure(taskId, code, reason, List.of());
    }

    public void schedulePublishFailure(
            long taskId, String code, String reason, List<PublishCheckError> checkErrors) {
        List<PublishCheckError> errors =
                checkErrors == null || checkErrors.isEmpty() ? List.of() : List.copyOf(checkErrors);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", taskId);
        summary.put("code", code);
        summary.put("reason", reason);
        if (!errors.isEmpty()) {
            summary.put("checkErrors", errors);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", "task");
        payload.put("action", "schedule-publish-failure");
        payload.put("operatorId", null);
        payload.put("operatorName", "system");
        payload.put("result", "FAILURE");
        payload.put("requestSummary", JsonUtil.toJson(summary));
        payload.put("errorMessage", errors.isEmpty() ? reason : JsonUtil.toJson(errors));
        eventPublisher.append(EventCodes.AUDIT_LOG, "task_definition", String.valueOf(taskId), payload);
    }
}
