package com.mkt.task.application;

import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.response.PublishCheckError;
import com.mkt.task.response.ScheduleFailureView;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskErrorCodes;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * Schedule-failure list / record / fingerprint helpers extracted from {@link TaskPublishAppService} (R1-6).
 * Move-only: scheduleFailures / recordScheduleFailure / failureAuditText / failureView unchanged.
 */
@Component
public class TaskPublishScheduleFailures {

    private static final Logger log = LoggerFactory.getLogger(TaskPublishScheduleFailures.class);
    static final String SCHEDULE_FAILURE_ACTION = "schedule-publish-failure";
    private final ConcurrentHashMap<Long, String> lastFailureFingerprint = new ConcurrentHashMap<>();

    private final ObjectProvider<TaskAuditAppender> audits;
    private final ObjectProvider<AlertWebhook> alerts;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final ObjectProvider<PlatformTransactionManager> txm;
    private final TaskAuditAppender auditsDirect;
    private final AlertWebhook alertsDirect;
    private final JdbcTemplate jdbcDirect;
    private final TransactionTemplate txDirect;

    @Autowired
    public TaskPublishScheduleFailures(
            ObjectProvider<TaskAuditAppender> audits,
            ObjectProvider<AlertWebhook> alerts,
            ObjectProvider<JdbcTemplate> jdbc,
            ObjectProvider<PlatformTransactionManager> txm) {
        this.audits = audits;
        this.alerts = alerts;
        this.jdbc = jdbc;
        this.txm = txm;
        this.auditsDirect = null;
        this.alertsDirect = null;
        this.jdbcDirect = null;
        this.txDirect = null;
    }

    TaskPublishScheduleFailures(
            TaskAuditAppender audits, AlertWebhook alerts, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.audits = null;
        this.alerts = null;
        this.jdbc = null;
        this.txm = null;
        this.auditsDirect = audits;
        this.alertsDirect = alerts;
        this.jdbcDirect = jdbc;
        this.txDirect = tx;
    }

    PageData<ScheduleFailureView> scheduleFailures(PageQuery page) {
        JdbcTemplate db = jdbc();
        if (db == null) {
            return new PageData<>(0, List.of());
        }
        PageQuery query = page == null ? PageQuery.of(1, 20) : page;
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_log WHERE module = ? AND action = ?",
                Long.class,
                "task",
                SCHEDULE_FAILURE_ACTION);
        List<ScheduleFailureView> rows = db.query(
                """
                SELECT id, request_summary, error_message, created_at
                FROM sys_audit_log
                WHERE module = ? AND action = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> failureView(
                        rs.getLong("id"),
                        rs.getString("request_summary"),
                        rs.getString("error_message"),
                        rs.getTimestamp("created_at")),
                "task",
                SCHEDULE_FAILURE_ACTION,
                query.pageSize(),
                query.offset());
        return new PageData<>(total == null ? 0L : total, rows);
    }

    void recordScheduleFailure(
            TaskDefinitionEntity row, String reason, List<PublishCheckError> checkErrors) {
        String message = reason == null || reason.isBlank()
                ? TaskErrorCodes.PUBLISH_VALIDATE_FAILED.message()
                : reason;
        String fingerprint = failureAuditText(message, checkErrors);
        String previous = lastFailureFingerprint.put(row.getId(), fingerprint);
        if (fingerprint.equals(previous)) {
            return;
        }
        try {
            runInTx(() -> {
                TaskAuditAppender appender = audits();
                if (appender != null) {
                    appender.schedulePublishFailure(row.getId(), row.getCode(), message, checkErrors);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("schedule-publish-failure audit failed, taskId={}", row.getId(), ex);
        }
        AlertWebhook webhook = alerts();
        if (webhook != null) {
            webhook.notifySchedulePublishFailure(row.getId(), row.getCode(), failureAuditText(message, checkErrors));
        }
    }

    void removeFailureFingerprint(Long taskId) {
        lastFailureFingerprint.remove(taskId);
    }

    private static String failureAuditText(String reason, List<PublishCheckError> checkErrors) {
        if (checkErrors != null && !checkErrors.isEmpty()) {
            return JsonUtil.toJson(checkErrors);
        }
        return reason;
    }

    private void runInTx(Runnable action) {
        runInTx(() -> {
            action.run();
            return true;
        });
    }

    private boolean runInTx(BooleanSupplier action) {
        TransactionTemplate template = tx();
        if (template != null) {
            Boolean result = template.execute(status -> action.getAsBoolean());
            return Boolean.TRUE.equals(result);
        }
        return action.getAsBoolean();
    }

    private TaskAuditAppender audits() {
        return auditsDirect != null ? auditsDirect : (audits == null ? null : audits.getIfAvailable());
    }

    private AlertWebhook alerts() {
        return alertsDirect != null ? alertsDirect : (alerts == null ? null : alerts.getIfAvailable());
    }

    private JdbcTemplate jdbc() {
        return jdbcDirect != null ? jdbcDirect : (jdbc == null ? null : jdbc.getIfAvailable());
    }

    private TransactionTemplate tx() {
        if (txDirect != null) {
            return txDirect;
        }
        if (txm == null) {
            return null;
        }
        PlatformTransactionManager manager = txm.getIfAvailable();
        return manager == null ? null : new TransactionTemplate(manager);
    }

    static ScheduleFailureView failureView(long id, String summary, String error, Timestamp created) {
        Long taskId = null;
        String code = null;
        String reason = error;
        if (summary != null && !summary.isBlank()) {
            try {
                JsonNode node = JsonUtil.readTree(summary);
                if (node.get("taskId") != null && node.get("taskId").isNumber()) {
                    taskId = node.get("taskId").asLong();
                }
                if (node.get("code") != null && !node.get("code").isNull()) {
                    code = node.get("code").asString();
                }
                JsonNode checkErrors = node.get("checkErrors");
                if (checkErrors != null && checkErrors.isArray() && checkErrors.size() > 0) {
                    reason = JsonUtil.toJson(checkErrors);
                }
            } catch (RuntimeException ignored) {
                // keep raw error
            }
        }
        Instant at = created == null ? null : created.toInstant();
        return new ScheduleFailureView(id, taskId, code, reason, at);
    }
}
