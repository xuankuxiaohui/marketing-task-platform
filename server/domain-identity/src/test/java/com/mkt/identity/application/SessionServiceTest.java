package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.support.SessionUsernames;
import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;
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
        String admin = sessions.loginAdmin(11L, 5, null, "alice");
        String client = sessions.loginClient(11L, 3, null, "bob_01");
        assertThat(admin).startsWith("admin:");
        assertThat(client).startsWith("client:");
        String adminRaw = admin.substring("admin:".length());
        String clientRaw = client.substring("client:".length());
        assertThat(sessions.adminSessionValid(adminRaw)).isTrue();
        assertThat(sessions.clientSessionValid(clientRaw)).isTrue();
        assertThat(SessionUsernames.read(StpAdmin.LOGIC, adminRaw, "11")).isEqualTo("alice");
        assertThat(SessionUsernames.read(StpClient.LOGIC, clientRaw, "11")).isEqualTo("bob_01");
        assertThat(sessions.adminSessionValid(client.substring("client:".length()))).isFalse();
        sessions.logoutAdmin(admin);
        assertThat(sessions.adminSessionValid(adminRaw)).isFalse();
        sessions.logoutClient(client);
        assertThat(sessions.clientSessionValid(clientRaw)).isFalse();
    }

    @Test
    void logoutAllInvalidatesEverySessionForTheAccount() {
        String first = sessions.loginAdmin(7L, 5, null, "ops");
        String second = sessions.loginAdmin(7L, 5, null, "ops");
        sessions.logoutAllAdmin(7L);
        assertThat(sessions.adminSessionValid(first.substring("admin:".length()))).isFalse();
        assertThat(sessions.adminSessionValid(second.substring("admin:".length()))).isFalse();
        String client = sessions.loginClient(9L, 3, null, "user_01");
        sessions.logoutAllClient(9L);
        assertThat(sessions.clientSessionValid(client.substring("client:".length()))).isFalse();
    }
}
