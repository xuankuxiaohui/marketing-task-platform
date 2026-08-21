package com.mkt.admin.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Fills the V1 empty super-admin hash from {@code MKT_INIT_ADMIN_PASSWORD} (design §3.1).
 *
 * <p>Do not add {@code @ConditionalOnBean(DataSource)}: component-scan evaluates that before
 * DataSource auto-config and skips this runner.
 *
 * <p>Keep exactly one constructor. A second constructor (even package-private) makes Spring fall
 * back to a missing no-arg ctor; compose then marks admin-app unhealthy.
 */
@Component
@Order(0)
public class InitAdminPasswordRunner implements ApplicationRunner {

    static final String ENV_PASSWORD = "MKT_INIT_ADMIN_PASSWORD";
    static final String ADMIN_USERNAME = "admin";

    private static final Logger log = LoggerFactory.getLogger(InitAdminPasswordRunner.class);

    private final JdbcTemplate jdbc;
    private final String initPassword;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public InitAdminPasswordRunner(
            JdbcTemplate jdbc, @Value("${" + ENV_PASSWORD + ":}") String initPassword) {
        this.jdbc = jdbc;
        this.initPassword = initPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM sys_admin_user WHERE username = ? AND deleted = 0",
                String.class,
                ADMIN_USERNAME);
        if (hash != null && !hash.isEmpty()) {
            return;
        }
        if (initPassword == null || initPassword.isBlank()) {
            log.warn("super-admin password_hash is empty; set {} to initialize (design §3.1)", ENV_PASSWORD);
            return;
        }
        jdbc.update(
                "UPDATE sys_admin_user SET password_hash = ?, must_change_password = 1 WHERE username = ? AND deleted = 0",
                encoder.encode(initPassword),
                ADMIN_USERNAME);
        log.info("super-admin password initialized from {}", ENV_PASSWORD);
    }
}
