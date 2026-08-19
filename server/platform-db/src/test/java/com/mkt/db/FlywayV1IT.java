package com.mkt.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V1 migrate + validate + second migrate is a no-op (design §6.9). Requires Docker; leave for CI.
 */
@Testcontainers
class FlywayV1IT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void migrateValidateIsIdempotentAndSeedsBaseline() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("1")
                .load();

        MigrateResult first = flyway.migrate();
        assertThat(first.migrationsExecuted).isEqualTo(1);
        flyway.validate();

        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();

        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'sys_%'",
                Integer.class);
        assertThat(tables).isEqualTo(12);

        Integer configs = jdbc.queryForObject("SELECT COUNT(*) FROM sys_config", Integer.class);
        assertThat(configs).isEqualTo(52);

        Integer menus = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE type = 'MENU'", Integer.class);
        assertThat(menus).isEqualTo(28);

        Integer dictTypes = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_type", Integer.class);
        assertThat(dictTypes).isEqualTo(6);

        Integer routes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_entry e JOIN sys_dict_type t ON t.id = e.type_id WHERE t.code = 'portal_route'",
                Integer.class);
        assertThat(routes).isEqualTo(8);

        Map<String, Object> admin = jdbc.queryForMap(
                "SELECT username, password_hash, must_change_password FROM sys_admin_user WHERE id = 1");
        assertThat(admin.get("username")).isEqualTo("admin");
        assertThat(admin.get("password_hash")).isEqualTo("");
        assertThat(((Number) admin.get("must_change_password")).intValue()).isEqualTo(1);

        Integer builtIn = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE built_in = 1 AND code = 'super-admin'", Integer.class);
        assertThat(builtIn).isEqualTo(1);

        Integer bindings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = 1", Integer.class);
        assertThat(bindings).isEqualTo(28);

        Integer nullable = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_audit_log' AND column_name = 'operator_id' AND is_nullable = 'YES'",
                Integer.class);
        assertThat(nullable).isEqualTo(1);

        List<String> indexes = jdbc.queryForList(
                "SELECT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'sys_outbox' AND index_name = 'idx_producer_status_next'",
                String.class);
        assertThat(indexes).isNotEmpty();
    }
}
