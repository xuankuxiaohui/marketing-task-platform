package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.application.SessionService;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.infra.session.KickReason;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;
import com.mkt.kernel.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionAuthFilterTest {

    private final MemoryKeyValueStore kv = new MemoryKeyValueStore();
    private final KickReasonStore kicks = new KickReasonStore(kv);
    private final SessionAvailability availability = new SessionAvailability(kv);

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void portalMissingTokenOnBusinessPathIs401Missing() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/api/common/task/mine"), response, unused());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("auth.session.missing");
    }

    @Test
    void portalAnonymousWithoutTokenPasses() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("POST", "/api/common/auth/login"), response, chain);
        assertThat(chain.called).isTrue();
        RecordingChain batch = new RecordingChain();
        filter.doFilter(request("POST", "/api/common/track/batch"), new MockHttpServletResponse(), batch);
        assertThat(batch.called).isTrue();
        RecordingChain internal = new RecordingChain();
        filter.doFilter(request("POST", "/internal/task/callback"), new MockHttpServletResponse(), internal);
        assertThat(internal.called).isTrue();
        RecordingChain activities = new RecordingChain();
        filter.doFilter(request("GET", "/api/common/activity/activities"), new MockHttpServletResponse(), activities);
        assertThat(activities.called).isTrue();
        RecordingChain taskList = new RecordingChain();
        filter.doFilter(request("GET", "/api/common/task/list"), new MockHttpServletResponse(), taskList);
        assertThat(taskList.called).isTrue();
    }

    @Test
    void adminTokenOnPortalIs401() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        MockHttpServletRequest req = request("GET", "/api/common/task/mine");
        req.addHeader("Authorization", "Bearer admin:abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, unused());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("auth.session.missing");
    }

    @Test
    void clientTokenOnAdminIs401Invalid() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.ADMIN, kicks, availability);
        MockHttpServletRequest req = request("GET", "/admin/auth/profile");
        req.addHeader("Authorization", "Bearer client:abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, unused());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("auth.session.invalid");
    }

    @Test
    void portalAdminKickOnRealTokenIs401KickedAdmin() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SessionService sessions = new SessionService(kicks);
        String token = sessions.loginClient(3L, 3, null, "bob_01");
        sessions.kickAllClient(3L);
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        MockHttpServletRequest req = request("GET", "/api/common/auth/profile");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, unused());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("auth.session.kicked-admin");
    }

    @Test
    void portalKickReasonIsConcurrent() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        kicks.write(StpClient.TYPE, "tok", KickReason.CONCURRENT);
        MockHttpServletRequest req = request("GET", "/api/common/task/mine");
        req.addHeader("Authorization", "Bearer client:tok");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, unused());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("auth.session.kicked-concurrent");
    }

    @Test
    void adminKickIsUnifiedInvalid() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.ADMIN, kicks, availability);
        kicks.write(StpAdmin.TYPE, "tok", KickReason.ADMIN);
        MockHttpServletRequest req = request("PUT", "/admin/auth/password");
        req.setCookies(new Cookie(AuthCookies.SESSION, "admin:tok"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, unused());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("auth.session.invalid");
    }

    @Test
    void adminAnonymousWithKickedCookieStillPasses() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.ADMIN, kicks, availability);
        kicks.write(StpAdmin.TYPE, "tok", KickReason.ADMIN);
        MockHttpServletRequest login = request("POST", "/admin/auth/login");
        login.setCookies(new Cookie(AuthCookies.SESSION, "admin:tok"));
        RecordingChain loginChain = new RecordingChain();
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        filter.doFilter(login, loginResponse, loginChain);
        assertThat(loginChain.called).isTrue();
        assertThat(loginResponse.getStatus()).isEqualTo(200);

        MockHttpServletRequest captcha = request("GET", "/admin/captcha");
        captcha.setCookies(new Cookie(AuthCookies.SESSION, "admin:tok"));
        RecordingChain captchaChain = new RecordingChain();
        MockHttpServletResponse captchaResponse = new MockHttpServletResponse();
        filter.doFilter(captcha, captchaResponse, captchaChain);
        assertThat(captchaChain.called).isTrue();
        assertThat(captchaResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void portalAnonymousWithKickedTokenStillPasses() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        kicks.write(StpClient.TYPE, "tok", KickReason.CONCURRENT);
        MockHttpServletRequest req = request("POST", "/api/common/auth/login");
        req.addHeader("Authorization", "Bearer client:tok");
        RecordingChain chain = new RecordingChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, chain);
        assertThat(chain.called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);

        MockHttpServletRequest captcha = request("GET", "/api/common/captcha");
        captcha.addHeader("Authorization", "Bearer client:tok");
        RecordingChain captchaChain = new RecordingChain();
        MockHttpServletResponse captchaResponse = new MockHttpServletResponse();
        filter.doFilter(captcha, captchaResponse, captchaChain);
        assertThat(captchaChain.called).isTrue();

        MockHttpServletRequest available = request("GET", "/api/common/auth/username-available");
        available.addHeader("Authorization", "Bearer client:tok");
        RecordingChain availableChain = new RecordingChain();
        filter.doFilter(available, new MockHttpServletResponse(), availableChain);
        assertThat(availableChain.called).isTrue();
    }

    @Test
    void authenticatedRequestSetsUsernameClearsContextAndRenewsCookie() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginAdmin(7L, 5, null, "alice");
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.ADMIN, kicks, availability);
        MockHttpServletRequest req = request("GET", "/admin/auth/profile");
        req.setCookies(new Cookie(AuthCookies.SESSION, token), new Cookie(AuthCookies.CSRF, "csrf-keep"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();
        chain.assertDuring = () -> {
            assertThat(UserContext.require().userId()).isEqualTo(7L);
            assertThat(UserContext.require().username()).isEqualTo("alice");
            assertThat(UserContext.require().username()).isNotEqualTo("7");
        };
        filter.doFilter(req, response, chain);
        assertThat(chain.called).isTrue();
        assertThat(UserContext.current()).isEmpty();
        String cookie = setCookie(response, AuthCookies.SESSION);
        assertThat(cookie).contains("Max-Age=" + AuthCookies.MAX_AGE_SECONDS);
        assertThat(cookie).contains(token);
        String csrf = setCookie(response, AuthCookies.CSRF);
        assertThat(csrf).startsWith(AuthCookies.CSRF + "=csrf-keep;");
        assertThat(csrf).contains("Max-Age=" + AuthCookies.MAX_AGE_SECONDS);
        assertThat(csrf).doesNotContain("HttpOnly");
        assertThat(response.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith(AuthCookies.CSRF + "=")))
                .hasSize(1);
    }

    @Test
    void authenticatedAdminRequestWithoutCsrfCookieDoesNotIssueCsrf() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginAdmin(7L, 5, null, "alice");
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.ADMIN, kicks, availability);
        MockHttpServletRequest req = request("GET", "/admin/auth/profile");
        req.setCookies(new Cookie(AuthCookies.SESSION, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, new RecordingChain());
        assertThat(setCookie(response, AuthCookies.SESSION)).contains(token);
        assertThat(response.getHeaders("Set-Cookie").stream().anyMatch(h -> h.startsWith(AuthCookies.CSRF + "=")))
                .isFalse();
    }

    @Test
    void portalLoginWithValidTokenDoesNotBindUserContext() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginClient(9L, 3, null, "bob");
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        MockHttpServletRequest req = request("POST", "/api/common/auth/login");
        req.addHeader("Authorization", "Bearer " + token);
        RecordingChain chain = new RecordingChain();
        chain.assertDuring = () -> assertThat(UserContext.current()).isEmpty();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.called).isTrue();
    }

    @Test
    void optionalAuthValidTokenBindsUserContext() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginClient(88L, 3, null, "u88");
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        for (String[] call : new String[][] {
            {"POST", "/api/common/track/batch"}, {"GET", "/api/common/ad/positions/home"}
        }) {
            MockHttpServletRequest req = request(call[0], call[1]);
            req.addHeader("Authorization", "Bearer " + token);
            RecordingChain chain = new RecordingChain();
            chain.assertDuring = () -> {
                assertThat(UserContext.require().userId()).isEqualTo(88L);
                assertThat(UserContext.require().username()).isEqualTo("u88");
                assertThat(UserContext.require().loginType()).isEqualTo(StpClient.TYPE);
            };
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(req, response, chain);
            assertThat(chain.called).as(call[1]).isTrue();
            assertThat(response.getStatus()).as(call[1]).isEqualTo(200);
            assertThat(UserContext.current()).isEmpty();
        }
    }

    @Test
    void optionalAuthKickedOrExpiredTokenPassesAnonymousWithout401() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        kicks.write(StpClient.TYPE, "kicked-tok", KickReason.CONCURRENT);
        MockHttpServletRequest kicked = request("POST", "/api/common/track/batch");
        kicked.addHeader("Authorization", "Bearer client:kicked-tok");
        RecordingChain kickedChain = new RecordingChain();
        kickedChain.assertDuring = () -> assertThat(UserContext.current()).isEmpty();
        MockHttpServletResponse kickedResponse = new MockHttpServletResponse();
        filter.doFilter(kicked, kickedResponse, kickedChain);
        assertThat(kickedChain.called).isTrue();
        assertThat(kickedResponse.getStatus()).isEqualTo(200);
        assertThat(kicks.peek(StpClient.TYPE, "kicked-tok")).contains(KickReason.CONCURRENT);

        String expired = new SessionService().loginClient(5L, 3, null, "gone");
        new SessionService().logoutClient(expired);
        MockHttpServletRequest expiredReq = request("GET", "/api/common/ad/positions/home");
        expiredReq.addHeader("Authorization", "Bearer " + expired);
        RecordingChain expiredChain = new RecordingChain();
        expiredChain.assertDuring = () -> assertThat(UserContext.current()).isEmpty();
        MockHttpServletResponse expiredResponse = new MockHttpServletResponse();
        filter.doFilter(expiredReq, expiredResponse, expiredChain);
        assertThat(expiredChain.called).isTrue();
        assertThat(expiredResponse.getStatus()).isEqualTo(200);
        assertThat(expiredResponse.getContentAsString()).doesNotContain("auth.session");
    }

    @Test
    void optionalAuthKickedTokenDoesNotConsumeKickReason() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        kicks.write(StpClient.TYPE, "tok", KickReason.CONCURRENT);
        MockHttpServletRequest batch = request("POST", "/api/common/track/batch");
        batch.addHeader("Authorization", "Bearer client:tok");
        filter.doFilter(batch, new MockHttpServletResponse(), new RecordingChain());

        MockHttpServletRequest required = request("GET", "/api/common/task/mine");
        required.addHeader("Authorization", "Bearer client:tok");
        MockHttpServletResponse requiredResponse = new MockHttpServletResponse();
        filter.doFilter(required, requiredResponse, unused());
        assertThat(requiredResponse.getStatus()).isEqualTo(401);
        assertThat(requiredResponse.getContentAsString()).contains("auth.session.kicked-concurrent");
    }

    @Test
    void optionalAuthCrossTokenIs401() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        for (String[] call : new String[][] {
            {"POST", "/api/common/track/batch"}, {"GET", "/api/common/ad/positions/home"}
        }) {
            MockHttpServletRequest req = request(call[0], call[1]);
            req.addHeader("Authorization", "Bearer admin:not-a-portal-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(req, response, unused());
            assertThat(response.getStatus()).as(call[1]).isEqualTo(401);
            assertThat(response.getContentAsString()).as(call[1]).contains("auth.session.missing");
        }
    }

    @Test
    void filterClearsUserContextWhenChainThrows() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginAdmin(7L, 5, null, "alice");
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.ADMIN, kicks, availability);
        MockHttpServletRequest req = request("GET", "/admin/auth/profile");
        req.setCookies(new Cookie(AuthCookies.SESSION, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(req, response, (r, s) -> {
                    assertThat(UserContext.require().username()).isEqualTo("alice");
                    throw new ServletException("boom");
                }))
                .isInstanceOf(ServletException.class);
        assertThat(UserContext.current()).isEmpty();
    }

    @Test
    void interceptorForbiddenWritesAuditForAdmin() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginAdmin(7L, 5, null, "alice");
        AtomicReference<String> audited = new AtomicReference<>();
        SessionAuthFilter filter = new SessionAuthFilter(
                SessionSide.ADMIN,
                kicks,
                availability,
                (userId, username, method, path, ip, userAgent) ->
                        audited.set(userId + ":" + username + ":" + method + ":" + path + ":" + ip));
        MockHttpServletRequest req = request("GET", "/admin/identity/roles");
        req.setRemoteAddr("10.0.0.9");
        req.setCookies(new Cookie(AuthCookies.SESSION, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, (r, s) -> {
            r.setAttribute(SessionAuthFilter.RBAC_DENIED, Boolean.TRUE);
            ((HttpServletResponse) s).setStatus(403);
        });
        assertThat(audited.get()).isEqualTo("7:alice:GET:/admin/identity/roles:10.0.0.9");
        com.mkt.kernel.audit.AuditOnce.mark();
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {});
        assertThat(com.mkt.kernel.audit.AuditOnce.written()).isFalse();
    }

    @Test
    void csrfForbiddenIsNotAuditedAsPermissionDenied() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginAdmin(7L, 5, null, "alice");
        AtomicReference<String> audited = new AtomicReference<>();
        SessionAuthFilter filter = new SessionAuthFilter(
                SessionSide.ADMIN,
                kicks,
                availability,
                (userId, username, method, path, ip, userAgent) -> audited.set("hit"));
        MockHttpServletRequest req = request("PUT", "/admin/identity/roles/1");
        req.setCookies(new Cookie(AuthCookies.SESSION, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req, response, (r, s) -> ((HttpServletResponse) s).setStatus(403));
        assertThat(audited.get()).isNull();
    }

    @Test
    void downstreamSeesUnwrappedAdminCookie() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        String token = new SessionService().loginAdmin(7L, 5, null, "alice");
        assertThat(token).startsWith("admin:");
        String raw = token.substring("admin:".length());
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.ADMIN, kicks, availability);
        MockHttpServletRequest req = request("GET", "/admin/identity/roles");
        req.setCookies(new Cookie(AuthCookies.SESSION, token), new Cookie(AuthCookies.CSRF, "csrf"));
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {
            HttpServletRequest http = (HttpServletRequest) r;
            for (Cookie cookie : http.getCookies()) {
                if (AuthCookies.SESSION.equals(cookie.getName())) {
                    seen.set(cookie.getValue());
                }
            }
        });
        assertThat(seen.get()).isEqualTo(raw);
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        return response.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElse("");
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRequestURI(path);
        return req;
    }

    private static FilterChain unused() {
        return (req, res) -> {
            throw new IllegalStateException("should not continue");
        };
    }

    private static final class RecordingChain implements FilterChain {
        boolean called;
        Runnable assertDuring;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
            if (assertDuring != null) {
                assertDuring.run();
            }
        }
    }
}
