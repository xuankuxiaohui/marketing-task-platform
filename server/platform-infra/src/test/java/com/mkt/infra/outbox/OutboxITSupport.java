package com.mkt.infra.outbox;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

final class OutboxITSupport {

    static MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.0").withDatabaseName("mkt_platform").withUsername("mkt").withPassword("mkt");
    }

    static JdbcTemplate migrate(MySQLContainer<?> mysql) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        return new JdbcTemplate(ds);
    }

    static DataSourceTransactionManager tx(JdbcTemplate jdbc) {
        return new DataSourceTransactionManager(jdbc.getDataSource());
    }

    private OutboxITSupport() {
    }
}
