package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AuthAttemptContext;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.kernel.BusinessException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R1.2 / R1.3: five wrong passwords through the Spring TX proxy persist locked_until and five
 * FAILURE audits. Requires Docker; leave for CI. Must not swallow exceptions inside a TX callback.
 */
@Testcontainers
class LoginLockCommitIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void fiveWrongPasswordsPersistLockAndFiveFailureAudits() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            env.jdbc.update(
                    "UPDATE sys_admin_user SET password_hash = ?, failed_attempts = 0, locked_until = NULL WHERE username = 'admin'",
                    new BCryptPasswordEncoder(12).encode("Abcdef12!x"));
            for (int i = 0; i < 5; i++) {
                int attempt = i;
                assertThatThrownBy(() -> loginWrong(env))
                        .isInstanceOf(BusinessException.class)
                        .extracting(ex -> ((BusinessException) ex).errorCode())
                        .isEqualTo(attempt == 4
                                ? AuthErrorCodes.LOGIN_LOCKED
                                : AuthErrorCodes.LOGIN_INVALID_CREDENTIAL);
            }
            Timestamp lockedUntil = env.jdbc.queryForObject(
                    "SELECT locked_until FROM sys_admin_user WHERE username = 'admin'", Timestamp.class);
            assertThat(lockedUntil).isNotNull();
            Integer attempts = env.jdbc.queryForObject(
                    "SELECT failed_attempts FROM sys_admin_user WHERE username = 'admin'", Integer.class);
            assertThat(attempts).isEqualTo(5);
            env.drain.awaitDrain(Duration.ofSeconds(5));
            Integer failures = env.jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM sys_audit_log
                    WHERE module = 'auth' AND action = 'login' AND result = 'FAILURE' AND operator_id IS NULL
                    """,
                    Integer.class);
            assertThat(failures).isEqualTo(5);
        }
    }

    private static void loginWrong(IdentityITSupport env) {
        var issued = env.captchas.issue(AdminAuthService.CAPTCHA_REALM);
        String code = env.captchaCode(AdminAuthService.CAPTCHA_REALM, issued.captchaId());
        env.adminAuth
                .login(
                        new AdminLoginCommand("admin", "WrongPass1!", issued.captchaId(), code, null),
                        new AuthAttemptContext("10.0.0.8", "it-agent", null))
                .orThrow();
    }
}
