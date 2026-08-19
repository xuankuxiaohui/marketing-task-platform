package com.mkt.identity.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.trace.TraceIds;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Outbox writer for identity RBAC mutations (R2.7). */
@Component
public class IdentityAuditAppender {

    private final EventPublisher publisher;

    public IdentityAuditAppender(EventPublisher publisher) {
        this.publisher = publisher;
    }

    public void append(String action, String aggregateType, String aggregateId, String result, String summary) {
        UserPrincipal principal = UserContext.current().orElse(null);
        Long operatorId = principal == null ? null : principal.userId();
        String operatorName = principal == null ? "" : principal.username();
        append(action, aggregateType, aggregateId, operatorId, operatorName, result, null, null, summary);
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", "identity");
        payload.put("action", action);
        payload.put("operatorId", operatorId);
        payload.put("operatorName", truncate(operatorName, 30));
        payload.put("ip", ip);
        payload.put("userAgent", truncate(userAgent, 255));
        payload.put("result", result);
        payload.put("requestSummary", truncate(summary, 2000));
        payload.put("traceId", TraceIds.current());
        String id = aggregateId == null || aggregateId.isBlank()
                ? (operatorId == null ? "unknown" : String.valueOf(operatorId))
                : aggregateId;
        publisher.append(EventCodes.AUDIT_LOG, aggregateType, id, payload);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        if (max <= 14) {
            return value.substring(0, max);
        }
        return value.substring(0, max - 14) + "...(truncated)";
    }
}
