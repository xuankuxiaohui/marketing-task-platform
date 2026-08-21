package com.mkt.task.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.task.response.InstanceEventView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Timeline source = pending {@code sys_outbox} ∪ consumed {@code evt_event_log} (design §6.4).
 * Dedupes by shared id (evt id reuses outbox id).
 */
@Repository
public class JdbcInstanceEventStore implements InstanceEventStore {

    private static final int SUMMARY_MAX = 512;
    private static final List<String> CODES = List.of(
            EventCodes.TASK_INSTANCE_START,
            EventCodes.TASK_STEP_COMPLETE,
            EventCodes.TASK_INSTANCE_COMPLETE,
            EventCodes.TASK_INSTANCE_ABANDON,
            EventCodes.TASK_INSTANCE_EXPIRE);

    private final JdbcTemplate jdbc;

    public JdbcInstanceEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<InstanceEventView> listByInstance(long instanceId, Long userId) {
        Map<Long, InstanceEventView> byId = new LinkedHashMap<>();
        for (DatedEvent row : loadOutbox(instanceId)) {
            byId.put(row.id(), toView(row));
        }
        for (DatedEvent row : loadEvt(instanceId, userId)) {
            byId.putIfAbsent(row.id(), toView(row));
        }
        List<InstanceEventView> views = new ArrayList<>(byId.values());
        views.sort(Comparator.comparing(InstanceEventView::time, Comparator.nullsLast(Comparator.naturalOrder())));
        return views;
    }

    private List<DatedEvent> loadOutbox(long instanceId) {
        String placeholders = String.join(",", CODES.stream().map(code -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add("task_instance");
        args.add(String.valueOf(instanceId));
        args.addAll(CODES);
        return jdbc.query(
                "SELECT id, event_code, payload, created_at FROM sys_outbox"
                        + " WHERE aggregate_type = ? AND aggregate_id = ? AND event_code IN ("
                        + placeholders
                        + ") ORDER BY id ASC",
                (rs, i) -> new DatedEvent(
                        rs.getLong("id"),
                        rs.getString("event_code"),
                        toInstant(rs.getTimestamp("created_at")),
                        rs.getString("payload")),
                args.toArray());
    }

    private List<DatedEvent> loadEvt(long instanceId, Long userId) {
        String placeholders = String.join(",", CODES.stream().map(code -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.addAll(CODES);
        args.add(instanceId);
        StringBuilder sql = new StringBuilder(
                "SELECT id, event_code, events, server_time FROM evt_event_log WHERE source = 'SERVER'"
                        + " AND event_code IN ("
                        + placeholders
                        + ") AND CAST(JSON_UNQUOTE(JSON_EXTRACT(events, '$[0].props.instanceId')) AS UNSIGNED) = ?");
        if (userId != null) {
            sql.append(" AND user_id = ?");
            args.add(userId);
        }
        sql.append(" ORDER BY server_time ASC, id ASC");
        return jdbc.query(
                sql.toString(),
                (rs, i) -> new DatedEvent(
                        rs.getLong("id"),
                        rs.getString("event_code"),
                        toInstant(rs.getTimestamp("server_time")),
                        rs.getString("events")),
                args.toArray());
    }

    private static InstanceEventView toView(DatedEvent row) {
        return new InstanceEventView(row.code(), row.time(), summarize(row.payload()));
    }

    private static String summarize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= SUMMARY_MAX ? raw : raw.substring(0, SUMMARY_MAX);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private record DatedEvent(long id, String code, Instant time, String payload) {}
}
