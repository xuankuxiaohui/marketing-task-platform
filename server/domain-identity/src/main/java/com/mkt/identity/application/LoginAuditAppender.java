package com.mkt.identity.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.identity.audit.AuditSummaries;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.audit.AuditOnce;
import com.mkt.kernel.trace.TraceIds;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoginAuditAppender {

    private final EventPublisher publisher;

    public LoginAuditAppender(EventPublisher publisher) {
        this.publisher = publisher;
    }

    public void append(
            String action,
            Long operatorId,
            String operatorName,
            String result,
            String ip,
            String userAgent,
            String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", "auth");
        payload.put("action", action);
        payload.put("operatorId", operatorId);
        payload.put("operatorName", AuditSummaries.cut(operatorName, 30));
        payload.put("ip", ip);
        payload.put("userAgent", AuditSummaries.cut(userAgent, 255));
        payload.put("result", result);
        payload.put("requestSummary", AuditSummaries.truncate(summary, AuditSummaries.MAX_CHARS));
        payload.put("traceId", TraceIds.current());
        String aggregateId = operatorId == null ? operatorName : String.valueOf(operatorId);
        publisher.append(EventCodes.AUDIT_LOG, "auth", aggregateId == null || aggregateId.isBlank() ? "unknown" : aggregateId, payload);
        AuditOnce.mark();
    }
}
