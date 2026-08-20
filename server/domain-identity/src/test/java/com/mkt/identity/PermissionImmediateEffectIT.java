package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AdminForbiddenAudit;
import com.mkt.identity.application.AuthAttemptContext;
import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.identity.controller.admin.RoleAdminController;
import com.mkt.identity.controller.admin.SaTokenExceptionHandler;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.identity.support.AdminStpInterface;
import com.mkt.identity.support.AuthCookies;
import com.mkt.identity.support.CsrfFilter;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.identity.support.SessionSide;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import com.mkt.kernel.web.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R2.2: removing a role permission is 403 on the next request with the same session; one audit row.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class PermissionImmediateEffectIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void removedPermissionIsDeniedImmediatelyWithAudit() throws Exception {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            String password = "Abcdef12!x";
            long queryId = insertOperation(env, "identity:role:query", "查询角色");
            long assignId = insertOperation(env, "identity:role:assign-permission", "分配权限");
            env.jdbc.update(
                    "INSERT INTO sys_role (id, code, name, status, built_in) VALUES (20, 'ops', '运营', 'ENABLED', 0)");
            env.jdbc.update(
                    "INSERT INTO sys_role_permission (role_id, permission_id) VALUES (20, ?), (20, ?)",
                    queryId,
                    assignId);
            env.jdbc.update(
                    """
                    INSERT INTO sys_admin_user (id, username, nickname, password_hash, status, deleted, must_change_password)
                    VALUES (20, 'operator', '运营', ?, 'ENABLED', 0, 0)
                    """,
                    new BCryptPasswordEncoder(12).encode(password));
            env.jdbc.update("INSERT INTO sys_admin_user_role (admin_user_id, role_id) VALUES (20, 20)");

            StpUtil.setStpLogic(StpAdmin.LOGIC);
            SaManager.setStpInterface(new AdminStpInterface(env.adminUsers));
            SessionAuthFilter session = new SessionAuthFilter(
                    SessionSide.ADMIN,
                    new KickReasonStore(env.kv),
                    new SessionAvailability(env.kv),
                    new AdminForbiddenAudit(env.identityAudits, env.txm));
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new RoleAdminController(env.roles))
                    .addFilters(new SaTokenContextFilterForJakartaServlet(), session, new CsrfFilter())
                    .addInterceptors(new SaInterceptor())
                    .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                    .build();

            var issued = env.captchas.issue(AdminAuthService.CAPTCHA_REALM);
            String code = env.captchaCode(AdminAuthService.CAPTCHA_REALM, issued.captchaId());
            AdminAuthService.IssuedAdminSession sessionTokens = env.adminAuth
                    .login(
                            new AdminLoginCommand("operator", password, issued.captchaId(), code, null),
                            new AuthAttemptContext("10.0.0.9", "it-agent", null))
                    .orThrow();
            assertThat(sessionTokens.body().permissions())
                    .contains("identity:role:query", "identity:role:assign-permission");

            Cookie satoken = new Cookie(AuthCookies.SESSION, sessionTokens.token());
            Cookie csrf = new Cookie(AuthCookies.CSRF, sessionTokens.csrfToken());
            mvc.perform(get("/admin/identity/roles").cookie(satoken, csrf))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            env.drain.awaitDrain(Duration.ofSeconds(5));
            Integer before = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_log WHERE action = 'permission-denied'", Integer.class);

            mvc.perform(put("/admin/identity/roles/20/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(AuthCookies.CSRF_HEADER, sessionTokens.csrfToken())
                            .cookie(satoken, csrf)
                            .content("{\"permissionIds\":[" + assignId + "]}"))
                    .andExpect(status().isOk());

            mvc.perform(get("/admin/identity/roles").cookie(satoken, csrf))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("common.permission-denied"));

            env.drain.awaitDrain(Duration.ofSeconds(5));
            Integer after = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_log WHERE action = 'permission-denied'", Integer.class);
            assertThat(after).isEqualTo((before == null ? 0 : before) + 1);
            assertThat(env.permissionCache.codesFor(20L)).containsExactly("identity:role:assign-permission");
        }
    }

    private static long insertOperation(IdentityITSupport env, String code, String name) {
        env.jdbc.update(
                """
                INSERT INTO sys_permission (parent_id, type, code, name, sort, status)
                VALUES (4, 'OPERATION', ?, ?, 1, 'ENABLED')
                """,
                code,
                name);
        return env.jdbc.queryForObject("SELECT id FROM sys_permission WHERE code = ?", Long.class, code);
    }
}
