package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.config.ConfigService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R23.1: replay the same aggregation window 3 times; counts stay at first-run values
 * and exclude simulated rows.
 */
@Testcontainers
class AggregationIdempotentIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void replayThreeTimesKeepsCountsAndDropsSimulated() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL.getJdbcUrl());
        hikari.setUsername(MYSQL.getUsername());
        hikari.setPassword(MYSQL.getPassword());
        try (HikariDataSource ds = new HikariDataSource(hikari)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Clock clock = Clock.fixed(now, MetricsAggregateService.SHANGHAI);
            seed(jdbc, now);
            ConfigService configs = (key, def) -> def;
            MetricsAggregateService aggregates = new MetricsAggregateService(jdbc, configs, clock);
            aggregates.run(now);
            Snapshot first = snapshot(jdbc);
            aggregates.run(now);
            aggregates.run(now);
            Snapshot third = snapshot(jdbc);
            assertThat(third).isEqualTo(first);
            assertThat(first.funnelExposure).isEqualTo(3);
            assertThat(first.funnelStart).isEqualTo(1);
            assertThat(first.funnelComplete).isEqualTo(1);
            assertThat(first.arrivedCount).isEqualTo(1);
            assertThat(first.arrivedCost).isEqualTo(88);
            assertThat(first.sendingCount).isEqualTo(1);
            assertThat(first.sendingCost).isEqualTo(7);
            assertThat(first.hitCount).isEqualTo(2);
            assertThat(first.interceptCount).isEqualTo(1);
            assertThat(first.adExposure).isEqualTo(2);
            assertThat(first.adClick).isEqualTo(1);
            assertThat(first.funnelDims).isEqualTo(1);
            MetricsQueryService queries = new MetricsQueryService(jdbc, clock);
            FunnelResponse funnel = queries.funnel(new MetricsQuery(null, null, MetricsGrain.DAY, "1"));
            assertThat(funnel.records()).hasSize(1);
            assertThat(funnel.records().getFirst().startRate()).isNotNull();
        }
    }

    private static void seed(JdbcTemplate jdbc, Instant now) {
        Timestamp ts = Timestamp.from(now);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                101L,
                "CLIENT",
                "task.card.exposure",
                events("task.card.exposure", 1, 2),
                2,
                1,
                0,
                ts);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                102L,
                "CLIENT",
                "task.card.exposure",
                events("task.card.exposure", 1, 1),
                1,
                1,
                0,
                ts);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                103L,
                "CLIENT",
                "task.card.exposure",
                events("task.card.exposure", 1, 99),
                99,
                1,
                1,
                ts);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                104L,
                "SERVER",
                "task.instance.start",
                serverEvent("task.instance.start", 1),
                1,
                1,
                0,
                ts);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                105L,
                "SERVER",
                "task.instance.complete",
                serverEvent("task.instance.complete", 1),
                1,
                1,
                0,
                ts);
        jdbc.update(
                "INSERT INTO evt_event_log (id, source, event_code, events, batch_size, registered, simulated, server_time)"
                        + " VALUES (?,?,?,?,?,?,?,?)",
                106L,
                "CLIENT",
                "ad.carousel.exposure",
                adEvents(2, 1),
                3,
                1,
                0,
                ts);
        jdbc.update(
                "INSERT INTO rwd_grant_record (prize_id, prize_code, category_code, cost_fen, user_id,"
                        + " grant_source, source_id, status, fulfillment_status, granted_at, simulated)"
                        + " VALUES (1,'p1','POINTS',88,9,'TASK_STEP','s1','GRANTED','ARRIVED',?,0)",
                ts);
        jdbc.update(
                "INSERT INTO rwd_grant_record (prize_id, prize_code, category_code, cost_fen, user_id,"
                        + " grant_source, source_id, status, fulfillment_status, granted_at, simulated)"
                        + " VALUES (1,'p1','POINTS',7,9,'TASK_STEP','s2','GRANTED','SENDING',?,0)",
                ts);
        jdbc.update(
                "INSERT INTO rwd_grant_record (prize_id, prize_code, category_code, cost_fen, user_id,"
                        + " grant_source, source_id, status, fulfillment_status, granted_at, simulated)"
                        + " VALUES (1,'p1','POINTS',999,9,'TASK_STEP','s3','GRANTED','ARRIVED',?,1)",
                ts);
        jdbc.update(
                "INSERT INTO risk_hit_log (id, hit_type, rule_code, context, hit_value, threshold,"
                        + " action_result, simulated, occurred_at)"
                        + " VALUES (1,'RULE','R-a','{}','1','30','REJECTED',0,?)",
                ts);
        jdbc.update(
                "INSERT INTO risk_hit_log (id, hit_type, rule_code, context, hit_value, threshold,"
                        + " action_result, simulated, occurred_at)"
                        + " VALUES (2,'RULE','R-a','{}','1','30','MARKED',0,?)",
                ts);
        jdbc.update(
                "INSERT INTO risk_hit_log (id, hit_type, rule_code, context, hit_value, threshold,"
                        + " action_result, simulated, occurred_at)"
                        + " VALUES (3,'RULE','R-a','{}','1','30','REJECTED',1,?)",
                ts);
    }

    private static String events(String code, long taskId, int copies) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < copies; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"code\":\"")
                    .append(code)
                    .append("\",\"props\":{\"taskId\":")
                    .append(taskId)
                    .append("}}");
        }
        return json.append(']').toString();
    }

    private static String serverEvent(String code, long taskId) {
        return "[{\"code\":\"" + code + "\",\"props\":{\"taskId\":" + taskId + "}}]";
    }

    private static String adEvents(int exposures, int clicks) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < exposures; i++) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"code\":\"ad.carousel.exposure\",\"props\":{\"positionCode\":\"home\",\"materialTrackId\":\"m1\"}}");
        }
        for (int i = 0; i < clicks; i++) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"code\":\"ad.carousel.click\",\"props\":{\"positionCode\":\"home\",\"materialTrackId\":\"m1\"}}");
        }
        return json.append(']').toString();
    }

    private static Snapshot snapshot(JdbcTemplate jdbc) {
        return new Snapshot(
                num(jdbc, "SELECT COALESCE(SUM(exposure_count),0) FROM mtr_task_funnel_d"),
                num(jdbc, "SELECT COALESCE(SUM(start_count),0) FROM mtr_task_funnel_d"),
                num(jdbc, "SELECT COALESCE(SUM(complete_count),0) FROM mtr_task_funnel_d"),
                num(jdbc, "SELECT COUNT(*) FROM mtr_task_funnel_d"),
                num(jdbc, "SELECT COALESCE(SUM(arrived_count),0) FROM mtr_reward_spend_d"),
                num(jdbc, "SELECT COALESCE(SUM(arrived_cost_fen),0) FROM mtr_reward_spend_d"),
                num(jdbc, "SELECT COALESCE(SUM(sending_count),0) FROM mtr_reward_spend_d"),
                num(jdbc, "SELECT COALESCE(SUM(sending_cost_fen),0) FROM mtr_reward_spend_d"),
                num(jdbc, "SELECT COALESCE(SUM(hit_count),0) FROM mtr_risk_hit_d"),
                num(jdbc, "SELECT COALESCE(SUM(intercept_count),0) FROM mtr_risk_hit_d"),
                num(jdbc, "SELECT COALESCE(SUM(exposure_count),0) FROM mtr_ad_material_d"),
                num(jdbc, "SELECT COALESCE(SUM(click_count),0) FROM mtr_ad_material_d"));
    }

    private static long num(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private record Snapshot(
            long funnelExposure,
            long funnelStart,
            long funnelComplete,
            long funnelDims,
            long arrivedCount,
            long arrivedCost,
            long sendingCount,
            long sendingCost,
            long hitCount,
            long interceptCount,
            long adExposure,
            long adClick) {}
}
