package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mkt.identity.domain.AdminPermissionCatalog;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.AdminUserMapper;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RbacPermissionCacheTest {

    @Test
    void superAdminGetsCatalogEvenWhenTreeHasNoOperations() {
        AdminUserMapper users = Mockito.mock(AdminUserMapper.class);
        RoleMapper roles = Mockito.mock(RoleMapper.class);
        PermissionMapper permissions = Mockito.mock(PermissionMapper.class);
        RoleEntity superAdmin = new RoleEntity();
        superAdmin.setId(1L);
        superAdmin.setBuiltIn(1);
        superAdmin.setStatus("ENABLED");
        when(roles.listByUserId(1L)).thenReturn(List.of(superAdmin));
        when(users.listPermissionCodes(1L)).thenReturn(List.of());
        when(permissions.listEnabledOperationCodes()).thenReturn(List.of());
        RbacPermissionCache cache =
                new RbacPermissionCache(new TwoLevelPlatformCache(new MemoryKeyValueStore()), users, roles, permissions);
        assertThat(cache.codesFor(1L)).contains("identity:role:query", "identity:role:assign-permission");
        assertThat(cache.codesFor(1L)).hasSize(AdminPermissionCatalog.P0.size());
    }

    @Test
    void disabledRoleCodesAreExcludedUntilEvictReloads() {
        AdminUserMapper users = Mockito.mock(AdminUserMapper.class);
        RoleMapper roles = Mockito.mock(RoleMapper.class);
        PermissionMapper permissions = Mockito.mock(PermissionMapper.class);
        RoleEntity ops = new RoleEntity();
        ops.setId(2L);
        ops.setBuiltIn(0);
        ops.setStatus("ENABLED");
        when(roles.listByUserId(3L)).thenReturn(List.of(ops));
        when(users.listPermissionCodes(3L)).thenReturn(List.of("identity:role:query"));
        RbacPermissionCache cache =
                new RbacPermissionCache(new TwoLevelPlatformCache(new MemoryKeyValueStore()), users, roles, permissions);
        assertThat(cache.codesFor(3L)).containsExactly("identity:role:query");
        when(users.listPermissionCodes(3L)).thenReturn(List.of());
        assertThat(cache.codesFor(3L)).containsExactly("identity:role:query");
        cache.evictAllAfterCommit();
        assertThat(cache.codesFor(3L)).isEmpty();
    }
}
