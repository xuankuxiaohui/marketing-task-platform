package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mkt.identity.domain.PermissionTypes;
import com.mkt.identity.domain.RoleStatuses;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.response.MenuNodeResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminMenuServiceTest {

    @Test
    void menusFilterToHeldSubtreeAndProfileUsesCache() {
        AdminUserStore users = Mockito.mock(AdminUserStore.class);
        PermissionMapper permissions = Mockito.mock(PermissionMapper.class);
        RbacPermissionCache cache = Mockito.mock(RbacPermissionCache.class);
        PermissionEntity menu = new PermissionEntity();
        menu.setId(4L);
        menu.setParentId(0L);
        menu.setType(PermissionTypes.MENU);
        menu.setName("角色权限");
        menu.setRoute("/system/roles");
        menu.setComponent("system/role/index");
        menu.setSort(4);
        menu.setStatus(RoleStatuses.ENABLED);
        when(permissions.listAllOrdered()).thenReturn(List.of(menu));
        when(permissions.listHeldIdsByUserId(2L)).thenReturn(List.of(4L));
        AdminUserEntity user = new AdminUserEntity();
        user.setId(2L);
        user.setUsername("ops");
        user.setNickname("运营");
        user.setDeleted(0);
        when(users.getById(2L)).thenReturn(user);
        when(users.listRoleCodes(2L)).thenReturn(List.of("ops"));
        when(cache.codesFor(2L)).thenReturn(List.of("identity:role:query"));
        AdminMenuService service = new AdminMenuService(users, permissions, cache);
        List<MenuNodeResponse> menus = service.menus(2L);
        assertThat(menus).extracting(MenuNodeResponse::route).containsExactly("/system/roles");
        assertThat(service.profile(2L).permissions()).containsExactly("identity:role:query");
    }
}
