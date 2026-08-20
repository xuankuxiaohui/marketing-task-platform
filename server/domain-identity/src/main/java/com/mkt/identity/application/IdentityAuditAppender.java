package com.mkt.identity.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.identity.audit.AuditSummaries;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.audit.AuditOnce;
import com.mkt.kernel.trace.TraceIds;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Outbox writer for identity mutations and AOP (R2.7 / R10.3). */
@Component
public class IdentityAuditAppender {

    private final EventPublisher publisher;

    public IdentityAuditAppender(EventPublisher publisher) {
        this.publisher = publisher;
    }

    public void append(String action, String aggregateType, String aggregateId, String result, String summary) {
        append("identity", action, aggregateType, aggregateId, result, summary);
    }

    public void append(
            String module, String action, String aggregateType, String aggregateId, String result, String summary) {
        UserPrincipal principal = UserContext.current().orElse(null);
        Long operatorId = principal == null ? null : principal.userId();
        String operatorName = principal == null ? "" : principal.username();
        append(
                module,
                action,
                aggregateType,
                aggregateId,
                operatorId,
                operatorName,
                result,
                null,
                null,
                summary,
                null,
                null);
    }

    public void append(
            String module,
            String action,
            String aggregateType,
            String aggregateId,
            String result,
            String ip,
            String userAgent,
            String summary,
            Integer costMs,
            String errorMessage) {
        UserPrincipal principal = UserContext.current().orElse(null);
        Long operatorId = principal == null ? null : principal.userId();
        String operatorName = principal == null ? "" : principal.username();
        append(
                module,
                action,
                aggregateType,
                aggregateId,
                operatorId,
                operatorName,
                result,
                ip,
                userAgent,
                summary,
                costMs,
                errorMessage);
    }

    public void append(
            String action,
            String aggregateType,
            String aggregateId,
            Long operatorId,
            String operatorName,
            String result,
            String ip,
            String userAgent,
            String summary) {
        append(
                "identity",
                action,
                aggregateType,
                aggregateId,
                operatorId,
                operatorName,
                result,
                ip,
                userAgent,
                summary,
                null,
                null);
    }

    public void append(
            String module,
            String action,
            String aggregateType,
            String aggregateId,
            Long operatorId,
            String operatorName,
            String result,
            String ip,
            String userAgent,
            String summary) {
        append(
                module,
                action,
                aggregateType,
                aggregateId,
                operatorId,
                operatorName,
                result,
                ip,
                userAgent,
                summary,
                null,
                null);
    }

    public void append(
            String module,
            String action,
            String aggregateType,
            String aggregateId,
            Long operatorId,
            String operatorName,
            String result,
            String ip,
            String userAgent,
            String summary,
            Integer costMs,
            String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", module == null || module.isBlank() ? "identity" : module);
        payload.put("action", action);
        payload.put("operatorId", operatorId);
        payload.put("operatorName", AuditSummaries.cut(operatorName, 30));
        payload.put("ip", ip);
        payload.put("userAgent", AuditSummaries.cut(userAgent, 255));
        payload.put("result", result);
        payload.put("requestSummary", AuditSummaries.truncate(summary, AuditSummaries.MAX_CHARS));
        payload.put("errorMessage", AuditSummaries.cut(errorMessage, 512));
        payload.put("costMs", costMs);
        payload.put("traceId", TraceIds.current());
        String type = aggregateType == null || aggregateType.isBlank() ? "audit" : aggregateType;
        String id = aggregateId == null || aggregateId.isBlank()
                ? (operatorId == null ? "unknown" : String.valueOf(operatorId))
                : aggregateId;
        publisher.append(EventCodes.AUDIT_LOG, type, id, payload);
        AuditOnce.mark();
    }
}
