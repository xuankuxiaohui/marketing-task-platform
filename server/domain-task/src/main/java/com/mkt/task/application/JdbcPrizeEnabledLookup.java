package com.mkt.task.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPrizeEnabledLookup implements PrizeEnabledLookup {

    private final JdbcTemplate jdbc;

    public JdbcPrizeEnabledLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean enabled(long prizeId) {
        String status = jdbc.query(
                "SELECT status FROM rwd_prize WHERE id = ? AND deleted = 0",
                rs -> rs.next() ? rs.getString(1) : null,
                prizeId);
        return "ENABLED".equals(status);
    }
}
