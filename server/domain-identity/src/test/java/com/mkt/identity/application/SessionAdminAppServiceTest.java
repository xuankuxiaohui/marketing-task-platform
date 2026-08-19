package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.mkt.identity.command.SessionKickCommand;
import com.mkt.identity.domain.AccountTypes;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.query.SessionQuery;
import com.mkt.identity.response.SessionView;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.infra.session.KickReason;
import com.mkt.infra.session.KickReasonStore;
import com.mkt.infra.session.StpAdmin;
import com.mkt.infra.session.StpClient;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SessionAdminAppServiceTest {

    private final AdminUserStore adminUsers = Mockito.mock(AdminUserStore.class);
    private final PortalUserStore portalUsers = Mockito.mock(PortalUserStore.class);
    private KickReasonStore kicks;
    private SessionService sessions;
    private SessionAdminAppService service;

    @BeforeEach
    void setUp() {
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        kicks = new KickReasonStore(new MemoryKeyValueStore());
        sessions = new SessionService(kicks);
        service = new SessionAdminAppService(sessions, adminUsers, portalUsers);
    }

    @Test
    void listsByAccountTypeAndAccountThenKicksAllSessions() {
        AdminUserEntity admin = new AdminUserEntity();
        admin.setId(11L);
        admin.setUsername("alice");
        when(adminUsers.getByUsername("alice")).thenReturn(admin);
        PortalUserEntity portal = new PortalUserEntity();
        portal.setId(22L);
        portal.setUsername("bob_01");
        when(portalUsers.getByUsername("bob_01")).thenReturn(portal);

        String first = sessions.loginAdmin(11L, 5, "dev-a", "alice", "10.0.0.1");
        sessions.loginAdmin(11L, 5, "dev-b", "alice", "10.0.0.2");
        String client = sessions.loginClient(22L, 3, "dev-c", "bob_01", "10.1.1.1");

        PageData<SessionView> adminRows =
                service.page(new SessionQuery(AccountTypes.ADMIN, "alice", PageQuery.of(1, 20)));
        assertThat(adminRows.total()).isEqualTo(2);
        assertThat(adminRows.records()).allMatch(row -> "alice".equals(row.account()));
        assertThat(adminRows.records()).allMatch(row -> AccountTypes.ADMIN.equals(row.accountType()));
        assertThat(adminRows.records()).allMatch(row -> row.tokenLast4() != null && row.tokenLast4().length() <= 4);
        assertThat(adminRows.records()).anyMatch(row -> "10.0.0.1".equals(row.ip()));
        assertThat(adminRows.records()).anyMatch(row -> "dev-a".equals(row.deviceId()));

        PageData<SessionView> portalRows =
                service.page(new SessionQuery(AccountTypes.PORTAL, "bob_01", PageQuery.of(1, 20)));
        assertThat(portalRows.total()).isEqualTo(1);
        assertThat(portalRows.records().get(0).accountType()).isEqualTo(AccountTypes.PORTAL);

        service.kick(new SessionKickCommand(AccountTypes.ADMIN, "alice", "xxxx"));
        String adminRaw = first.substring("admin:".length());
        assertThat(sessions.adminSessionValid(adminRaw)).isFalse();
        assertThat(kicks.peek(StpAdmin.LOGIC.getLoginType(), adminRaw)).contains(KickReason.ADMIN);
        String clientRaw = client.substring("client:".length());
        assertThat(sessions.clientSessionValid(clientRaw)).isTrue();
        service.kick(new SessionKickCommand(AccountTypes.PORTAL, "bob_01", null));
        assertThat(sessions.clientSessionValid(clientRaw)).isFalse();
        assertThat(kicks.peek(StpClient.LOGIC.getLoginType(), clientRaw)).contains(KickReason.ADMIN);
    }

    @Test
    void unknownAccountIsNotFoundAndBadTypeIsParamInvalid() {
        when(adminUsers.getByUsername("nobody")).thenReturn(null);
        assertThatThrownBy(() -> service.kick(new SessionKickCommand(AccountTypes.ADMIN, "nobody", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.NOT_FOUND);
        assertThatThrownBy(
                        () -> service.page(new SessionQuery("other", "alice", PageQuery.of(1, 20))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }
}
