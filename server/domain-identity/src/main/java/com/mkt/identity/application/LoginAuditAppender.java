package com.mkt.identity.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
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
        payload.put("operatorName", truncate(operatorName, 30));
        payload.put("ip", ip);
        payload.put("userAgent", truncate(userAgent, 255));
        payload.put("result", result);
        payload.put("requestSummary", truncate(summary, 2000));
        payload.put("traceId", TraceIds.current());
        String aggregateId = operatorId == null ? operatorName : String.valueOf(operatorId);
        publisher.append(EventCodes.AUDIT_LOG, "auth", aggregateId == null ? "unknown" : aggregateId, payload);
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
