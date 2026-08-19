package com.mkt.risk.application;

import com.mkt.infra.outbox.ConsumerDirection;
import com.mkt.infra.outbox.EventConsumer;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.infra.outbox.OutboxRoutes;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.risk.domain.RiskCntKeys;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.support.RiskCntWindow;
import com.mkt.risk.support.RiskListImportParser;
import java.time.Clock;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Async {@code risk:cnt} writer (design §5.9). R-e is never written.
 */
@Component
public class RiskCntConsumer implements EventConsumer {

    private final RiskCntWindow window;
    private final Clock clock;

    public RiskCntConsumer(RiskCntWindow window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    @Override
    public ConsumerDirection direction() {
        return ConsumerDirection.RISK_CNT;
    }

    @Override
    public void consume(OutboxRecord row) {
        long now = clock.instant().toEpochMilli();
        boolean simulated = Boolean.TRUE.equals(readBoolean(row.payload(), "simulated"));
        if (OutboxRoutes.TASK_INSTANCE_COMPLETE.equals(row.eventCode())) {
            if (simulated) {
                return;
            }
            Long userId = readLong(row.payload(), "userId");
            Long instanceId = readLong(row.payload(), "instanceId");
            if (userId == null || instanceId == null) {
                return;
            }
            window.add(RiskRuleCode.RA, RiskCntKeys.DIM_USER, String.valueOf(userId), String.valueOf(instanceId), now);
            return;
        }
        if (OutboxRoutes.REWARD_GRANT_SUCCESS.equals(row.eventCode())) {
            if (simulated) {
                return;
            }
            Long userId = readLong(row.payload(), "userId");
            Long recordId = readLong(row.payload(), "recordId");
            if (userId == null || recordId == null) {
                return;
            }
            window.add(RiskRuleCode.RB, RiskCntKeys.DIM_USER, String.valueOf(userId), String.valueOf(recordId), now);
            return;
        }
        if (OutboxRoutes.AUTH_REGISTER_SUCCESS.equals(row.eventCode())
                || OutboxRoutes.AUTH_LOGIN_SUCCESS.equals(row.eventCode())) {
            Long userId = readLong(row.payload(), "userId");
            if (userId == null) {
                return;
            }
            String account = String.valueOf(userId);
            String ip = normalize(RiskDimension.IP, readText(row.payload(), "ip"));
            String device = normalize(RiskDimension.DEVICE, readText(row.payload(), "deviceId"));
            if (ip != null) {
                window.add(RiskRuleCode.RC, RiskCntKeys.DIM_IP, ip, account, now);
            }
            if (device != null) {
                window.add(RiskRuleCode.RD, RiskCntKeys.DIM_DEVICE, device, account, now);
            }
        }
    }

    private static String normalize(RiskDimension dimension, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return RiskListImportParser.normalizeOrNull(dimension, raw);
    }

    private static Long readLong(String payload, String field) {
        JsonNode value = field(payload, field);
        return value == null || !value.isNumber() ? null : value.asLong();
    }

    private static Boolean readBoolean(String payload, String field) {
        JsonNode value = field(payload, field);
        return value == null || !value.isBoolean() ? null : value.asBoolean();
    }

    private static String readText(String payload, String field) {
        JsonNode value = field(payload, field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static JsonNode field(String payload, String field) {
        if (payload == null || payload.isBlank() || "null".equals(payload)) {
            return null;
        }
        try {
            return JsonUtil.readTree(payload).get(field);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
