package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.identity.command.PermissionSaveCommand;
import com.mkt.identity.domain.PermissionTypes;
import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.identity.mapper.RolePermissionMapper;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PermissionAppServiceTest {

    private final PermissionMapper permissions = Mockito.mock(PermissionMapper.class);
    private final RoleMapper roles = Mockito.mock(RoleMapper.class);
    private final RolePermissionMapper rolePermissions = Mockito.mock(RolePermissionMapper.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private PermissionAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "admin"));
        RbacPermissionCache cache = new RbacPermissionCache(
                new TwoLevelPlatformCache(new MemoryKeyValueStore()),
                Mockito.mock(com.mkt.identity.mapper.AdminUserMapper.class),
                roles,
                permissions);
        service = new PermissionAppService(
                permissions,
                roles,
                rolePermissions,
                cache,
                new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                new MutableClock(Instant.parse("2026-08-19T12:00:00Z")));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void createOperationMustHangOnMenuAndBindsSuperAdmin() {
        PermissionEntity menu = new PermissionEntity();
        menu.setId(4L);
        menu.setType(PermissionTypes.MENU);
        when(permissions.selectById(4L)).thenReturn(menu);
        when(permissions.insert(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        });
        RoleEntity superAdmin = new RoleEntity();
        superAdmin.setId(1L);
        superAdmin.setCode("super-admin");
        superAdmin.setBuiltIn(1);
        when(roles.getByCode("super-admin")).thenReturn(superAdmin);

        PermissionEntity created = service.create(new PermissionSaveCommand(
                4L, "OPERATION", "identity:role:query", "查询角色", null, null, null, 1));
        assertThat(created.getCode()).isEqualTo("identity:role:query");
        verify(rolePermissions).insert(1L, 99L);
        assertThat(outbox.all()).isNotEmpty();
    }

    @Test
    void menuMustNotCarryOperationCode() {
        assertThatThrownBy(() -> service.create(new PermissionSaveCommand(
                        0L, "MENU", "identity:role:query", "角色", "/system/roles", "system/role/index", null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void operationWithoutMenuRejected() {
        assertThatThrownBy(() -> service.create(new PermissionSaveCommand(
                        0L, "OPERATION", "identity:role:query", "查询", null, null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void deleteWithChildrenRejected() {
        PermissionEntity menu = new PermissionEntity();
        menu.setId(4L);
        menu.setType(PermissionTypes.MENU);
        menu.setName("角色");
        when(permissions.selectById(4L)).thenReturn(menu);
        when(permissions.countByParentId(4L)).thenReturn(2);
        assertThatThrownBy(() -> service.delete(4L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
        verify(permissions, Mockito.never()).deleteById(anyLong());
    }
}
