package com.mkt.infra.outbox;

import com.mkt.kernel.json.JsonUtil;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Writes {@code evt_event_log} with id = sys_outbox.id; PK conflict = success (design §6.4). */
public final class EvtEventLogWriter implements EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EvtEventLogWriter.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public EvtEventLogWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public ConsumerDirection direction() {
        return ConsumerDirection.EVT_EVENT_LOG;
    }

    @Override
    public void consume(OutboxRecord row) {
        if (OutboxRoutes.AUDIT_LOG.equals(row.eventCode())) {
            return;
        }
        Instant serverTime = clock.instant();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", row.eventCode());
        item.put("props", parseProps(row.payload()));
        item.put("clientTime", null);
        String events = JsonUtil.toJson(List.of(item));
        Long userId = readLong(row.payload(), "userId");
        int simulated = Boolean.TRUE.equals(readBoolean(row.payload(), "simulated")) ? 1 : 0;
        try {
            jdbc.update(
                    "INSERT INTO evt_event_log (id, source, event_code, user_id, events, batch_size, registered, simulated, server_time)"
                            + " VALUES (?,?,?,?,?,1,1,?,?)",
                    row.id(),
                    "SERVER",
                    row.eventCode(),
                    userId,
                    events,
                    simulated,
                    Timestamp.from(serverTime));
        } catch (DuplicateKeyException duplicate) {
            log.info("evt_event_log duplicate treated as success id={}", row.id());
        }
    }

    private static Object parseProps(String payload) {
        if (payload == null || payload.isBlank() || "null".equals(payload)) {
            return Map.of();
        }
        try {
            JsonNode node = JsonUtil.readTree(payload);
            if (node.isObject() || node.isArray()) {
                return JsonUtil.fromJson(payload, Object.class);
            }
            return Map.of("raw", payload);
        } catch (RuntimeException ex) {
            return Map.of("raw", payload);
        }
    }

    private static Long readLong(String payload, String field) {
        try {
            JsonNode node = JsonUtil.readTree(payload);
            JsonNode value = node.get(field);
            return value == null || !value.isNumber() ? null : value.asLong();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Boolean readBoolean(String payload, String field) {
        try {
            JsonNode node = JsonUtil.readTree(payload);
            JsonNode value = node.get(field);
            return value == null || !value.isBoolean() ? null : value.asBoolean();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
