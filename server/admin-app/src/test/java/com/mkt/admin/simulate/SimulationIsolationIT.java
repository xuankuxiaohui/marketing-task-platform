package com.mkt.admin.simulate;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.admin.metrics.MetricsAggregateService;
import com.mkt.admin.metrics.MetricsGrain;
import com.mkt.admin.metrics.MetricsQuery;
import com.mkt.admin.metrics.MetricsQueryService;
import com.mkt.identity.config.ConfigService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R24.1: after simulated rows exist, R23 aggregates and R27 hit counts stay at real-only values.
 * Frequency / association rule behaviour is covered by {@code RiskCheckPortImplTest}.
 */
@Testcontainers
class SimulationIsolationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void aggregatesDropSimulatedEventsGrantsAndHits() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL.getJdbcUrl());
        hikari.setUsername(MYSQL.getUsername());
        hikari.setPassword(MYSQL.getPassword());
        try (HikariDataSource ds = new HikariDataSource(hikari)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Clock clock = Clock.fixed(now, ZoneId.of("Asia/Shanghai"));
            seed(jdbc, now);
            ConfigService configs = (key, def) -> def;
            MetricsAggregateService aggregates = new MetricsAggregateService(jdbc, configs, clock);
            aggregates.run(now);
            assertThat(num(jdbc, "SELECT COALESCE(SUM(start_count),0) FROM mtr_task_funnel_d")).isEqualTo(1);
            assertThat(num(jdbc, "SELECT COALESCE(SUM(arrived_count),0) FROM mtr_reward_spend_d")).isEqualTo(1);
            assertThat(num(jdbc, "SELECT COALESCE(SUM(hit_count),0) FROM mtr_risk_hit_d")).isEqualTo(1);
            assertThat(num(jdbc, "SELECT COALESCE(SUM(intercept_count),0) FROM mtr_risk_hit_d")).isEqualTo(1);
            MetricsQueryService queries = new MetricsQueryService(jdbc, clock);
            assertThat(queries.funnel(new MetricsQuery(null, null, MetricsGrain.DAY, "1")).records()).hasSize(1);
            assertThat(queries.risk(new MetricsQuery(null, null, MetricsGrain.DAY, "R-a")).records()).hasSize(1);
        }
    }

    private static void seed(JdbcTemplate jdbc, Instant now) {
        Timestamp ts = Timestamp.from(now);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                201L,
                "SERVER",
                "task.instance.start",
                "[{\"code\":\"task.instance.start\",\"props\":{\"taskId\":1}}]",
                1,
                1,
                0,
                ts);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                202L,
                "SERVER",
                "task.instance.start",
                "[{\"code\":\"task.instance.start\",\"props\":{\"taskId\":1}}]",
                1,
                1,
                1,
                ts);
        jdbc.update(
                "INSERT INTO rwd_grant_record (prize_id, prize_code, category_code, cost_fen, user_id,"
                        + " grant_source, source_id, status, fulfillment_status, granted_at, simulated)"
                        + " VALUES (1,'p1','POINTS',88,9,'TASK_STEP','s1','GRANTED','ARRIVED',?,0)",
                ts);
        jdbc.update(
                "INSERT INTO rwd_grant_record (prize_id, prize_code, category_code, cost_fen, user_id,"
                        + " grant_source, source_id, status, fulfillment_status, granted_at, simulated)"
                        + " VALUES (1,'p1','POINTS',999,9,'TASK_STEP','s2','GRANTED','ARRIVED',?,1)",
                ts);
        jdbc.update(
                "INSERT INTO risk_hit_log (id, hit_type, rule_code, context, hit_value, threshold,"
                        + " action_result, simulated, occurred_at)"
                        + " VALUES (11,'RULE','R-a','{}','1','30','REJECTED',0,?)",
                ts);
        jdbc.update(
                "INSERT INTO risk_hit_log (id, hit_type, rule_code, context, hit_value, threshold,"
                        + " action_result, simulated, occurred_at)"
                        + " VALUES (12,'RULE','R-a','{}','1','30','REJECTED',1,?)",
                ts);
    }

    private static long num(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
