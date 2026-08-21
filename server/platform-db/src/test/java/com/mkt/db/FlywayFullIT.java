package com.mkt.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V1–V6 migrate + validate + second migrate is a no-op (design §6.9). Requires Docker; leave for CI.
 */
@Testcontainers
class FlywayFullIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void migrateCreatesFortyFourTablesWithSeedsAndPartition() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult first = flyway.migrate();
        assertThat(first.migrationsExecuted).isEqualTo(6);
        flyway.validate();

        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
                        + " AND table_name <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tables).isEqualTo(44);

        assertThat(indexExists(jdbc, "task_instance", "uk_user_task_cycle")).isTrue();
        assertThat(indexExists(jdbc, "task_progress_report", "uk_dedup")).isTrue();
        assertThat(indexExists(jdbc, "rwd_grant_record", "uk_idempotent")).isTrue();
        assertThat(indexExists(jdbc, "pnt_account", "uk_user")).isTrue();
        assertThat(indexExists(jdbc, "risk_list_item", "uk_dim_type_value")).isTrue();
        assertThat(indexExists(jdbc, "sgn_record", "uk_activity_user_date")).isTrue();
        assertThat(indexExists(jdbc, "sgn_activity_snapshot", "uk_activity_version")).isTrue();
        Integer signinPerms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE id BETWEEN 29 AND 38", Integer.class);
        assertThat(signinPerms).isEqualTo(10);
        assertThat(indexExists(jdbc, "act_activity", "uk_code")).isTrue();
        Integer activityPerms = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE id BETWEEN 39 AND 47", Integer.class);
        assertThat(activityPerms).isEqualTo(9);

        assertThat(columnExists(jdbc, "task_instance_step", "skip_reason")).isTrue();
        assertThat(columnExists(jdbc, "task_instance_step", "last_biz_no")).isTrue();
        assertThat(columnExists(jdbc, "rwd_grant_record", "next_retry_at")).isTrue();
        assertThat(columnExists(jdbc, "rwd_prize", "version")).isFalse();

        for (String table : List.of(
                "task_instance", "rwd_grant_record", "pnt_transaction", "risk_hit_log", "evt_event_log")) {
            assertThat(columnExists(jdbc, table, "simulated")).as(table + ".simulated").isTrue();
        }

        Integer categories = jdbc.queryForObject("SELECT COUNT(*) FROM rwd_prize_category", Integer.class);
        assertThat(categories).isEqualTo(7);

        Integer reconReview = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rwd_prize_category"
                        + " WHERE code IN ('ALIPAY_RED','WECHAT_RED','PHONE_CREDIT')"
                        + " AND recon_required = 1 AND recon_action_policy = 'REVIEW'",
                Integer.class);
        assertThat(reconReview).isEqualTo(3);

        Integer rules = jdbc.queryForObject("SELECT COUNT(*) FROM risk_rule_config", Integer.class);
        assertThat(rules).isEqualTo(6);

        Integer reWindow = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk_rule_config WHERE rule_code = 'R-e' AND window_seconds IS NULL",
                Integer.class);
        assertThat(reWindow).isEqualTo(1);

        Integer metadata = jdbc.queryForObject("SELECT COUNT(*) FROM evt_event_metadata", Integer.class);
        assertThat(metadata).isEqualTo(36);

        List<String> partitions = jdbc.queryForList(
                "SELECT DISTINCT partition_name FROM information_schema.partitions"
                        + " WHERE table_schema = DATABASE() AND table_name = 'evt_event_log'"
                        + " AND partition_name IS NOT NULL",
                String.class);
        assertThat(partitions).isNotEmpty();
        assertThat(partitions.getFirst()).matches("p\\d{6}");
    }

    private static boolean indexExists(JdbcTemplate jdbc, String table, String index) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics"
                        + " WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                table,
                index);
        return n != null && n > 0;
    }

    private static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column);
        return n != null && n > 0;
    }
}
