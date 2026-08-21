package com.mkt.admin.simulate;

import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.domain.StockChangeTypes;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC lookup for simulated grants and reverse traces. */
public final class JdbcSimulateGrantLookup implements SimulateGrantLookup {

    private final JdbcTemplate jdbc;

    public JdbcSimulateGrantLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SimulateGrantRow> listByStepSourceIds(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(sourceIds.size(), "?"));
        String sql =
                "SELECT id, prize_id, user_id, grant_source, source_id, fulfillment_status, category_code"
                        + " FROM rwd_grant_record WHERE grant_source = 'TASK_STEP' AND simulated = 1"
                        + " AND source_id IN ("
                        + placeholders
                        + ") ORDER BY id";
        return jdbc.query(
                sql,
                (rs, rowNum) -> new SimulateGrantRow(
                        rs.getLong("id"),
                        rs.getLong("prize_id"),
                        rs.getLong("user_id"),
                        rs.getString("grant_source"),
                        rs.getString("source_id"),
                        rs.getString("fulfillment_status"),
                        rs.getString("category_code")),
                sourceIds.toArray());
    }

    @Override
    public boolean reverseLogExists(long grantRecordId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rwd_stock_log WHERE change_type = ? AND biz_id = ?",
                Integer.class,
                StockChangeTypes.SIMULATE_REVERSE,
                String.valueOf(grantRecordId));
        return n != null && n > 0;
    }

    @Override
    public Long earnAmount(long userId, String bizSource, String bizId) {
        List<Long> amounts = jdbc.query(
                "SELECT amount FROM pnt_transaction WHERE user_id = ? AND type = ? AND biz_source = ?"
                        + " AND biz_id = ? AND simulated = 1 ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong("amount"),
                userId,
                PointTypes.EARN,
                bizSource,
                bizId);
        return amounts.isEmpty() ? null : amounts.getFirst();
    }

    @Override
    public boolean reversalExists(String bizId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pnt_transaction WHERE type = ? AND biz_id = ? AND simulated = 1",
                Integer.class,
                PointTypes.REVERSAL,
                bizId);
        return n != null && n > 0;
    }
}
