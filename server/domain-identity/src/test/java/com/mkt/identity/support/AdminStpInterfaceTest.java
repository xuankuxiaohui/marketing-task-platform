package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mkt.identity.application.AdminUserStore;
import com.mkt.infra.session.StpAdmin;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminStpInterfaceTest {

    @Test
    void loadsAdminPermissionsAndIgnoresClient() {
        AdminUserStore users = Mockito.mock(AdminUserStore.class);
        when(users.listPermissionCodes(3L)).thenReturn(List.of("identity:admin-user:query"));
        when(users.listRoleCodes(3L)).thenReturn(List.of("ops"));
        AdminStpInterface stp = new AdminStpInterface(users);
        assertThat(stp.getPermissionList(3L, StpAdmin.TYPE)).containsExactly("identity:admin-user:query");
        assertThat(stp.getRoleList(3L, StpAdmin.TYPE)).containsExactly("ops");
        assertThat(stp.getPermissionList(3L, "client")).isEmpty();
    }
}
