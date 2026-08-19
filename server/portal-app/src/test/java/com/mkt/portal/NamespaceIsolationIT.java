package com.mkt.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.identity.support.SessionSide;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.portal.namespace.NamespaceGuardFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * R4.1: admin tokens cannot call portal common endpoints; foreign namespace is 404.
 * Filter-stack IT; Docker not required.
 */
class NamespaceIsolationIT {

    private static final List<PathCall> COMMON = List.of(
            new PathCall("GET", "/api/common/captcha"),
            new PathCall("GET", "/api/common/auth/username-available"),
            new PathCall("POST", "/api/common/auth/register"),
            new PathCall("POST", "/api/common/auth/login"),
            new PathCall("POST", "/api/common/auth/logout"),
            new PathCall("GET", "/api/common/auth/profile"),
            new PathCall("PUT", "/api/common/auth/profile"),
            new PathCall("PUT", "/api/common/auth/password"),
            new PathCall("GET", "/api/common/task/list"),
            new PathCall("GET", "/api/common/task/1/detail"),
            new PathCall("POST", "/api/common/task/1/start"),
            new PathCall("POST", "/api/common/task/instances/1/steps/go/click"),
            new PathCall("POST", "/api/common/task/instances/1/abandon"),
            new PathCall("GET", "/api/common/task/mine"),
            new PathCall("GET", "/api/common/prize/list"),
            new PathCall("POST", "/api/common/prize/records/1/claim"),
            new PathCall("GET", "/api/common/points/balance"),
            new PathCall("GET", "/api/common/points/transactions"),
            new PathCall("GET", "/api/common/dict/province"),
            new PathCall("POST", "/api/common/track/batch"));

    @Test
    void adminTokenRejectedOnAllCommonEndpoints() throws Exception {
        assertThat(COMMON).hasSize(20);
        MemoryKeyValueStore kv = new MemoryKeyValueStore();
        SessionAuthFilter filter =
                new SessionAuthFilter(SessionSide.PORTAL, new KickReasonStore(kv), new SessionAvailability(kv));
        for (PathCall call : COMMON) {
            MockHttpServletRequest req = new MockHttpServletRequest(call.method, call.path);
            req.setRequestURI(call.path);
            req.addHeader("Authorization", "Bearer admin:not-a-portal-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(req, response, new MockFilterChain());
            assertThat(response.getStatus()).as(call.path).isEqualTo(401);
            assertThat(response.getContentAsString()).as(call.path).contains("auth.session.missing");
        }
    }

    @Test
    void portalAppForeignNamespaceIs404AndAdminAppRejectsClientToken() throws Exception {
        NamespaceGuardFilter portalGuard = new NamespaceGuardFilter();
        MockHttpServletRequest adminOnPortal = new MockHttpServletRequest("GET", "/admin/auth/login");
        adminOnPortal.setRequestURI("/admin/auth/login");
        MockHttpServletResponse portal404 = new MockHttpServletResponse();
        portalGuard.doFilter(adminOnPortal, portal404, new MockFilterChain());
        assertThat(portal404.getStatus()).isEqualTo(404);

        MemoryKeyValueStore kv = new MemoryKeyValueStore();
        SessionAuthFilter adminFilter =
                new SessionAuthFilter(SessionSide.ADMIN, new KickReasonStore(kv), new SessionAvailability(kv));
        MockHttpServletRequest clientOnAdmin = new MockHttpServletRequest("GET", "/admin/auth/profile");
        clientOnAdmin.setRequestURI("/admin/auth/profile");
        clientOnAdmin.addHeader("Authorization", "Bearer client:portal-token");
        MockHttpServletResponse admin401 = new MockHttpServletResponse();
        adminFilter.doFilter(clientOnAdmin, admin401, new MockFilterChain());
        assertThat(admin401.getStatus()).isEqualTo(401);
        assertThat(admin401.getContentAsString()).contains("auth.session.invalid");
    }

    private record PathCall(String method, String path) {}
}
