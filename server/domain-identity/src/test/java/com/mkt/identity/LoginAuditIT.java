package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AuthAttemptContext;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.it.IdentityITSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R1.2: every login attempt produces exactly one audit row. Requires Docker; leave for CI. */
@Testcontainers
class LoginAuditIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void twoHundredAttemptsProduceTwoHundredAuditRows() throws Exception {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            String password = "Abcdef12!x";
            env.jdbc.update(
                    "UPDATE sys_admin_user SET password_hash = ?, failed_attempts = 0, locked_until = NULL WHERE username = 'admin'",
                    new BCryptPasswordEncoder(12).encode(password));
            int total = 200;
            ExecutorService pool = Executors.newFixedThreadPool(64);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                boolean success = ThreadLocalRandom.current().nextBoolean();
                futures.add(pool.submit(() -> {
                    start.await();
                    attempt(env, password, success);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            env.drain.awaitDrain(Duration.ofSeconds(5));
            Integer audits = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_log WHERE module = 'auth' AND action = 'login'", Integer.class);
            assertThat(audits).isEqualTo(total);
            Integer withFields = env.jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM sys_audit_log
                    WHERE module = 'auth' AND action = 'login'
                      AND operator_name IS NOT NULL AND operator_name <> ''
                      AND ip IS NOT NULL AND user_agent IS NOT NULL
                      AND result IN ('SUCCESS','FAILURE')
                      AND created_at IS NOT NULL
                    """,
                    Integer.class);
            assertThat(withFields).isEqualTo(total);
        }
    }

    private static void attempt(IdentityITSupport env, String password, boolean success) {
        var issued = env.captchas.issue(AdminAuthService.CAPTCHA_REALM);
        String code = env.captchaCode(AdminAuthService.CAPTCHA_REALM, issued.captchaId());
        try {
            env.adminAuth
                    .login(
                            new AdminLoginCommand(
                                    "admin",
                                    success ? password : "WrongPass1!",
                                    issued.captchaId(),
                                    code,
                                    null),
                            new AuthAttemptContext("10.0.0.8", "it-agent", null))
                    .orThrow();
        } catch (RuntimeException ignored) {
            // after the TX proxy committed lock/audit
        }
    }
}
