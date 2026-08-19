package com.mkt.infra.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

public final class JdbcOutboxStore implements OutboxStore {

    private static final RowMapper<OutboxRecord> ROW = (rs, i) -> new OutboxRecord(
            rs.getLong("id"),
            rs.getString("event_code"),
            rs.getString("producer"),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            rs.getString("payload"),
            rs.getString("status"),
            rs.getInt("retry_count"),
            ts(rs.getTimestamp("next_retry_at")),
            ts(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public JdbcOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insert(String eventCode, String producer, String aggregateType, String aggregateId, String payload) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                con -> {
                    var ps = con.prepareStatement(
                            "INSERT INTO sys_outbox (event_code, producer, aggregate_type, aggregate_id, payload, status, retry_count)"
                                    + " VALUES (?,?,?,?,?,'PENDING',0)",
                            new String[] {"id"});
                    ps.setString(1, eventCode);
                    ps.setString(2, producer);
                    ps.setString(3, aggregateType);
                    ps.setString(4, aggregateId);
                    ps.setString(5, payload);
                    return ps;
                },
                keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("sys_outbox insert returned no id");
        }
        return id.longValue();
    }

    @Override
    public List<OutboxRecord> claimBatch(String producer, Instant now, int limit) {
        return jdbc.query(
                "SELECT id, event_code, producer, aggregate_type, aggregate_id, payload, status, retry_count, next_retry_at, created_at"
                        + " FROM sys_outbox WHERE producer = ? AND status = 'PENDING'"
                        + " AND (next_retry_at IS NULL OR next_retry_at <= ?)"
                        + " ORDER BY id LIMIT ?",
                ROW,
                producer,
                Timestamp.from(now),
                limit);
    }

    @Override
    public void delete(long id) {
        jdbc.update("DELETE FROM sys_outbox WHERE id = ?", id);
    }

    @Override
    public void markRetry(long id, int retryCount, Instant nextRetryAt) {
        jdbc.update(
                "UPDATE sys_outbox SET retry_count = ?, next_retry_at = ? WHERE id = ?",
                retryCount,
                Timestamp.from(nextRetryAt),
                id);
    }

    @Override
    public void markDead(long id, int retryCount) {
        jdbc.update("UPDATE sys_outbox SET retry_count = ?, status = 'DEAD' WHERE id = ?", retryCount, id);
    }

    @Override
    public int countPending(String producer) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_outbox WHERE producer = ? AND status = 'PENDING'", Integer.class, producer);
        return n == null ? 0 : n;
    }

    private static Instant ts(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
