package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AuthAttemptContext;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.command.ChangePasswordCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.kernel.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R1.3: password change keeps current session and kills the others. Requires Docker; leave for CI. */
@Testcontainers
class PasswordChangeSessionIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void currentSessionSurvivesOtherTwoDie() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            String password = "Abcdef12!x";
            env.jdbc.update(
                    "UPDATE sys_admin_user SET password_hash = ?, failed_attempts = 0, locked_until = NULL WHERE username = 'admin'",
                    new BCryptPasswordEncoder(12).encode(password));
            var s1 = login(env, password);
            var s2 = login(env, password);
            var s3 = login(env, password);
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.adminAuth.changePassword(
                            1L, new ChangePasswordCommand("not-old", "NewPass12!x"), s1.token())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(AuthErrorCodes.PASSWORD_OLD_MISMATCH);
            env.tx.executeWithoutResult(status -> env.adminAuth.changePassword(
                    1L, new ChangePasswordCommand(password, "NewPass12!x"), s1.token()));
            String raw1 = s1.token().substring("admin:".length());
            String raw2 = s2.token().substring("admin:".length());
            String raw3 = s3.token().substring("admin:".length());
            assertThat(env.sessions.adminSessionValid(raw1)).isTrue();
            assertThat(env.sessions.adminSessionValid(raw2)).isFalse();
            assertThat(env.sessions.adminSessionValid(raw3)).isFalse();
        }
    }

    private static AdminAuthService.IssuedAdminSession login(IdentityITSupport env, String password) {
        var issued = env.captchas.issue(AdminAuthService.CAPTCHA_REALM);
        String code = env.captchaCode(AdminAuthService.CAPTCHA_REALM, issued.captchaId());
        return env.adminAuth
                .login(
                        new AdminLoginCommand("admin", password, issued.captchaId(), code, null),
                        new AuthAttemptContext("10.0.0.8", "it-agent", null))
                .orThrow();
    }
}
