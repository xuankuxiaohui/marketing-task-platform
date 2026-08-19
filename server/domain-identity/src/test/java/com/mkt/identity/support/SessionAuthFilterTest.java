package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.infra.session.KickReason;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;
import com.mkt.kernel.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
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
        filter.doFilter(request("GET", "/api/common/task/list"), response, unused());
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
    }

    @Test
    void adminTokenOnPortalIs401() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        MockHttpServletRequest req = request("GET", "/api/common/task/list");
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
    void portalKickReasonIsConcurrent() throws Exception {
        SessionAuthFilter filter = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        kicks.write(StpClient.TYPE, "tok", KickReason.CONCURRENT);
        MockHttpServletRequest req = request("GET", "/api/common/task/list");
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

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }
}
