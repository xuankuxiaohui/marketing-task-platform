package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

    private final SessionService sessions = new SessionService();

    @BeforeEach
    void dao() {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
    }

    @Test
    void adminAndClientTokensAreIsolated() {
        String admin = sessions.loginAdmin(11L, 5, null);
        String client = sessions.loginClient(11L, 3, null);
        assertThat(admin).startsWith("admin:");
        assertThat(client).startsWith("client:");
        assertThat(sessions.adminSessionValid(admin.substring("admin:".length()))).isTrue();
        assertThat(sessions.clientSessionValid(client.substring("client:".length()))).isTrue();
        assertThat(sessions.adminSessionValid(client.substring("client:".length()))).isFalse();
        sessions.logoutAdmin(admin);
        assertThat(sessions.adminSessionValid(admin.substring("admin:".length()))).isFalse();
        sessions.logoutClient(client);
        assertThat(sessions.clientSessionValid(client.substring("client:".length()))).isFalse();
    }
}
