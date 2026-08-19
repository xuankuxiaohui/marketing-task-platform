package com.mkt.risk.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.risk.support.RiskOperator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes {@code audit.log} in the business transaction (R25.8 / R27.1).
 * TODO(task-25): drop this once {@code @Audited} AOP is the only writer.
 */
public class RiskAuditAppender {

    private final EventPublisher eventPublisher;

    public RiskAuditAppender(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void append(String action, String aggregateType, String aggregateId, String summary) {
        long operatorId = RiskOperator.requireUserId();
        UserPrincipal principal = UserContext.current().orElseThrow();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", "risk");
        payload.put("action", action);
        payload.put("operatorId", operatorId);
        payload.put("operatorName", truncate(principal.username(), 30));
        payload.put("result", "SUCCESS");
        payload.put("requestSummary", truncate(summary, 2000));
        eventPublisher.append(EventCodes.AUDIT_LOG, aggregateType, aggregateId, payload);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
