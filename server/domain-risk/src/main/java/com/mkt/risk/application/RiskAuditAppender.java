package com.mkt.risk.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.audit.AuditOnce;
import com.mkt.risk.support.RiskOperator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes {@code audit.log} in the business transaction (R25.8 / R27.1).
 * Marks {@link AuditOnce} so {@code @Audited} AOP does not insert a second row.
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
        AuditOnce.mark();
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
