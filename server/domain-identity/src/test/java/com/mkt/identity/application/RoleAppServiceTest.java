package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.identity.command.RoleAssignPermissionsCommand;
import com.mkt.identity.command.RoleCreateCommand;
import com.mkt.identity.command.RoleUpdateCommand;
import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.AdminUserRoleMapper;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.identity.mapper.RolePermissionMapper;
import com.mkt.identity.query.RoleQuery;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RoleAppServiceTest {

    private final RoleMapper roles = Mockito.mock(RoleMapper.class);
    private final AdminUserRoleMapper userRoles = Mockito.mock(AdminUserRoleMapper.class);
    private final RolePermissionMapper rolePermissions = Mockito.mock(RolePermissionMapper.class);
    private final PermissionMapper permissions = Mockito.mock(PermissionMapper.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private PlatformCache cache;
    private RoleAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "admin"));
        cache = new TwoLevelPlatformCache(new MemoryKeyValueStore());
        RbacPermissionCache rbac = new RbacPermissionCache(
                cache, Mockito.mock(com.mkt.identity.mapper.AdminUserMapper.class), roles, permissions);
        service = new RoleAppService(
                roles,
                userRoles,
                rolePermissions,
                permissions,
                rbac,
                new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                new MutableClock(Instant.parse("2026-08-19T12:00:00Z")));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void createThenBuiltInDeleteRejectedAndAssignEvicts() {
        when(roles.insert(any(RoleEntity.class))).thenAnswer(invocation -> {
            RoleEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });
        RoleEntity created = service.create(new RoleCreateCommand("ops-a", "运营", "d"));
        assertThat(created.getCode()).isEqualTo("ops-a");
        assertThat(outbox.all()).hasSize(1);

        RoleEntity builtIn = new RoleEntity();
        builtIn.setId(1L);
        builtIn.setCode("super-admin");
        builtIn.setBuiltIn(1);
        builtIn.setStatus("ENABLED");
        when(roles.selectById(1L)).thenReturn(builtIn);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.ROLE_BUILT_IN);
        verify(roles, never()).deleteById(1L);
        verify(userRoles, never()).deleteByRoleId(1L);
        assertThatThrownBy(() -> service.assignPermissions(1L, new RoleAssignPermissionsCommand(List.of(4L))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.ROLE_BUILT_IN);
        verify(rolePermissions, never()).deleteByRoleId(1L);
        assertThatThrownBy(() -> service.update(1L, new RoleUpdateCommand("超管", "d", "DISABLED")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.ROLE_BUILT_IN);

        RoleEntity ops = new RoleEntity();
        ops.setId(9L);
        ops.setCode("ops-a");
        ops.setBuiltIn(0);
        ops.setStatus("ENABLED");
        when(roles.selectById(9L)).thenReturn(ops);
        PermissionEntity node = new PermissionEntity();
        node.setId(4L);
        when(permissions.selectById(4L)).thenReturn(node);
        cache.put(CacheNamespace.RBAC_PERMISSION, "2", new String[] {"identity:role:query"});
        when(rolePermissions.listPermissionIds(9L)).thenReturn(List.of(4L, 10L));
        assertThat(service.listPermissionIds(9L)).containsExactly(4L, 10L);
        service.assignPermissions(9L, new RoleAssignPermissionsCommand(List.of(4L)));
        verify(rolePermissions).deleteByRoleId(9L);
        verify(rolePermissions).insert(9L, 4L);
        assertThat(cache.get(CacheNamespace.RBAC_PERMISSION, "2", String[].class, () -> new String[] {"x"}))
                .containsExactly("x");
    }

    @Test
    void updateDisableEvictsAndPageAllEnabled() {
        RoleEntity ops = new RoleEntity();
        ops.setId(2L);
        ops.setCode("ops");
        ops.setName("运营");
        ops.setStatus("ENABLED");
        ops.setBuiltIn(0);
        when(roles.selectById(2L)).thenReturn(ops);
        when(roles.updateById(any(RoleEntity.class))).thenReturn(1);
        cache.put(CacheNamespace.RBAC_PERMISSION, "8", new String[] {"identity:role:query"});
        service.update(2L, new RoleUpdateCommand("运营2", "x", "DISABLED"));
        assertThat(cache.get(CacheNamespace.RBAC_PERMISSION, "8", String[].class, () -> new String[] {"miss"}))
                .containsExactly("miss");

        when(roles.listEnabled()).thenReturn(List.of(ops));
        when(userRoles.countUsers(anyLong())).thenReturn(3);
        var page = service.page(new RoleQuery(true, PageQuery.of(1, 20)));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).userCount()).isEqualTo(3);
    }

    @Test
    void listPermissionIdsRejectsMissingRole() {
        when(roles.selectById(8L)).thenReturn(null);
        assertThatThrownBy(() -> service.listPermissionIds(8L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.NOT_FOUND);
        verify(rolePermissions, never()).listPermissionIds(8L);
    }

    @Test
    void deleteNonBuiltInClearsBindings() {
        RoleEntity ops = new RoleEntity();
        ops.setId(9L);
        ops.setCode("ops-a");
        ops.setBuiltIn(0);
        ops.setStatus("ENABLED");
        when(roles.selectById(9L)).thenReturn(ops);
        service.delete(9L);
        verify(rolePermissions).deleteByRoleId(9L);
        verify(userRoles).deleteByRoleId(9L);
        verify(roles).deleteById(9L);
        assertThat(outbox.all()).isNotEmpty();
    }
}
