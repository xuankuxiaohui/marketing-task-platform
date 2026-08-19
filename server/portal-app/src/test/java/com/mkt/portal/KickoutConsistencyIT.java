package com.mkt.portal;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.application.SessionService;
import com.mkt.identity.support.SessionAuthFilter;
import com.mkt.identity.support.SessionSide;
import com.mkt.infra.degrade.SessionAvailability;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.infra.session.KickReasonStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * R6.1: kick on instance A is 401 on instance B for the same token (shared session store).
 */
class KickoutConsistencyIT {

    @Test
    void kickOnOneInstanceRejectsTheOtherImmediately() throws Exception {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        MemoryKeyValueStore kv = new MemoryKeyValueStore();
        KickReasonStore kicks = new KickReasonStore(kv);
        SessionAvailability availability = new SessionAvailability(kv);
        SessionService sessions = new SessionService(kicks);
        String token = sessions.loginClient(9L, 3, "dev-1", "bob_01", "10.0.0.2");

        SessionAuthFilter instanceA = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        SessionAuthFilter instanceB = new SessionAuthFilter(SessionSide.PORTAL, kicks, availability);
        MockHttpServletResponse before = new MockHttpServletResponse();
        instanceB.doFilter(bearerGet(token), before, new MockFilterChain());
        assertThat(before.getStatus()).isEqualTo(200);

        sessions.kickAllClient(9L);

        MockHttpServletResponse afterA = new MockHttpServletResponse();
        instanceA.doFilter(bearerGet(token), afterA, new MockFilterChain());
        MockHttpServletResponse afterB = new MockHttpServletResponse();
        instanceB.doFilter(bearerGet(token), afterB, new MockFilterChain());
        assertThat(afterA.getStatus()).isEqualTo(401);
        assertThat(afterB.getStatus()).isEqualTo(401);
        assertThat(afterB.getContentAsString()).contains("auth.session.kicked-admin");
        MockHttpServletResponse replay = new MockHttpServletResponse();
        instanceB.doFilter(bearerGet(token), replay, new MockFilterChain());
        assertThat(replay.getStatus()).isEqualTo(401);
    }

    private static MockHttpServletRequest bearerGet(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/common/auth/profile");
        req.setRequestURI("/api/common/auth/profile");
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }
}
