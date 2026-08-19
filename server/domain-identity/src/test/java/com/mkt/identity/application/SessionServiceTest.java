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

    @Test
    void listExposesIpDeviceAndKickAllInvalidates() {
        com.mkt.infra.session.KickReasonStore kicks =
                new com.mkt.infra.session.KickReasonStore(new com.mkt.infra.redis.MemoryKeyValueStore());
        SessionService withKicks = new SessionService(kicks);
        String token = withKicks.loginClient(4L, 3, "phone-1", "user_01", "10.9.9.9");
        var rows = withKicks.listClient(4L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).account()).isEqualTo("user_01");
        assertThat(rows.get(0).accountType()).isEqualTo("portal");
        assertThat(rows.get(0).ip()).isEqualTo("10.9.9.9");
        assertThat(rows.get(0).deviceId()).isEqualTo("phone-1");
        withKicks.kickAllClient(4L);
        assertThat(withKicks.clientSessionValid(token.substring("client:".length()))).isFalse();
    }
}
