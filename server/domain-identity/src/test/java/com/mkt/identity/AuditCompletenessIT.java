package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AdminForbiddenAudit;
import com.mkt.identity.application.AuthAttemptContext;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.command.ConfigCreateCommand;
import com.mkt.identity.command.DictTypeCreateCommand;
import com.mkt.identity.command.RoleCreateCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R10.1: annotated admin writes produce one audit row each, success or failure.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class AuditCompletenessIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void thirtyWritesProduceThirtyAuditRows() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            UserContext.set(new UserPrincipal(1L, "admin", "admin"));
            String password = "Abcdef12!x";
            env.jdbc.update(
                    "UPDATE sys_admin_user SET password_hash = ?, failed_attempts = 0, locked_until = NULL WHERE username = 'admin'",
                    new BCryptPasswordEncoder(12).encode(password));

            for (int i = 0; i < 10; i++) {
                env.dictApp.createType(new DictTypeCreateCommand("audit_t" + i, "审计类型" + i, null));
            }
            for (int i = 0; i < 5; i++) {
                env.roles.create(new RoleCreateCommand("audr" + i, "审计角色" + i, null));
            }
            for (int i = 0; i < 5; i++) {
                env.configApp.create(new ConfigCreateCommand(
                        "audit.it.key." + i, "audit-it", "1", "NUMBER", false, null));
            }

            for (int i = 0; i < 5; i++) {
                failedLogin(env, password);
            }
            AdminForbiddenAudit forbidden = new AdminForbiddenAudit(env.identityAudits, env.txm);
            for (int i = 0; i < 5; i++) {
                forbidden.onForbidden(1L, "admin", "GET", "/admin/identity/roles", "10.0.0.9", "it-agent");
            }

            env.drain.awaitDrain(Duration.ofSeconds(5));
            Integer audits = env.jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log", Integer.class);
            assertThat(audits).isEqualTo(30);
            Integer failures = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_log WHERE result = 'FAILURE'", Integer.class);
            assertThat(failures).isEqualTo(10);
        } finally {
            UserContext.clear();
        }
    }

    private static void failedLogin(IdentityITSupport env, String password) {
        var issued = env.captchas.issue(AdminAuthService.CAPTCHA_REALM);
        String code = env.captchaCode(AdminAuthService.CAPTCHA_REALM, issued.captchaId());
        try {
            env.adminAuth
                    .login(
                            new AdminLoginCommand("admin", "WrongPass1!", issued.captchaId(), code, null),
                            new AuthAttemptContext("10.0.0.8", "it-agent", null))
                    .orThrow();
        } catch (RuntimeException ignored) {
            // committed failure audit
        }
    }
}
