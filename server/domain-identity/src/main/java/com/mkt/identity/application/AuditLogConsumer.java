package com.mkt.identity.application;

import com.mkt.infra.outbox.ConsumerDirection;
import com.mkt.infra.outbox.EventConsumer;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.kernel.json.JsonUtil;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Relays {@code audit.log} into {@code sys_audit_log} (design §6.5). */
@Component
public class AuditLogConsumer implements EventConsumer {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuditLogConsumer(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public ConsumerDirection direction() {
        return ConsumerDirection.SYS_AUDIT_LOG;
    }

    @Override
    public void consume(OutboxRecord row) {
        JsonNode payload = parse(row.payload());
        String module = text(payload, "module", "auth");
        String action = text(payload, "action", "unknown");
        Long operatorId = number(payload, "operatorId");
        String operatorName = truncate(text(payload, "operatorName", ""), 30);
        if (operatorName.isEmpty()) {
            operatorName = "unknown";
        }
        String ip = text(payload, "ip", null);
        String userAgent = truncate(text(payload, "userAgent", null), 255);
        String summary = truncate(text(payload, "requestSummary", null), 2000);
        String result = text(payload, "result", "SUCCESS");
        String error = truncate(text(payload, "errorMessage", null), 512);
        Integer costMs = intValue(payload, "costMs");
        String traceId = text(payload, "traceId", null);
        Instant created = clock.instant();
        jdbc.update(
                """
                INSERT INTO sys_audit_log
                (module, action, operator_id, operator_name, ip, user_agent, request_summary, result, error_message, cost_ms, trace_id, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                module,
                action,
                operatorId,
                operatorName,
                ip,
                userAgent,
                summary,
                result,
                error,
                costMs,
                traceId,
                Timestamp.from(created));
    }

    private static JsonNode parse(String payload) {
        if (payload == null || payload.isBlank() || "null".equals(payload)) {
            return JsonUtil.readTree("{}");
        }
        return JsonUtil.readTree(payload);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asLong();
    }

    private static Integer intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
