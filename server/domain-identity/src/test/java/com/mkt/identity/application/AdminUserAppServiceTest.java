package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.identity.command.AdminUserCreateCommand;
import com.mkt.identity.command.AdminUserResetPasswordCommand;
import com.mkt.identity.command.AdminUserUpdateCommand;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.AdminUserMapper;
import com.mkt.identity.mapper.AdminUserRoleMapper;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.identity.query.AdminUserQuery;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdminUserAppServiceTest {

    private final AdminUserMapper users = Mockito.mock(AdminUserMapper.class);
    private final AdminUserRoleMapper userRoles = Mockito.mock(AdminUserRoleMapper.class);
    private final RoleMapper roles = Mockito.mock(RoleMapper.class);
    private final SessionService sessions = Mockito.mock(SessionService.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private AdminUserAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(99L, "admin", "ops"));
        service = new AdminUserAppService(
                users,
                userRoles,
                roles,
                new PasswordHasher(),
                sessions,
                new RbacPermissionCache(
                        new TwoLevelPlatformCache(new MemoryKeyValueStore()), users, roles, Mockito.mock(PermissionMapper.class)),
                new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                new MutableClock(Instant.parse("2026-08-19T12:00:00Z")));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void createAllowsEmptyRolesAndRejectsDuplicateUsername() {
        when(users.insert(any(AdminUserEntity.class))).thenAnswer(invocation -> {
            AdminUserEntity entity = invocation.getArgument(0);
            entity.setId(8L);
            return 1;
        });
        AdminUserEntity created =
                service.create(new AdminUserCreateCommand("Ops_User", "运营", "Abcdef12!x", List.of()));
        assertThat(created.getUsername()).isEqualTo("ops_user");
        verify(userRoles).deleteByUserId(8L);
        verify(userRoles, never()).insert(anyLong(), anyLong());
        assertThat(outbox.all()).isNotEmpty();

        when(users.insert(any(AdminUserEntity.class))).thenThrow(new DuplicateKeyException("uk"));
        assertThatThrownBy(() -> service.create(new AdminUserCreateCommand("ops_user", "运营", "Abcdef12!x", List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.USERNAME_DUPLICATE);
    }

    @Test
    void disableSelfOrBuiltInIsRejectedAndDisableLogsOut() {
        AdminUserEntity self = liveUser(99L, "ops");
        when(users.selectById(99L)).thenReturn(self);
        assertThatThrownBy(() -> service.disable(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.USER_SELF_PROTECTED);
        verify(sessions, never()).logoutAllAdmin(99L);

        AdminUserEntity admin = liveUser(1L, "admin");
        when(users.selectById(1L)).thenReturn(admin);
        when(roles.countEnabledBuiltInByUserId(1L)).thenReturn(1);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.USER_SELF_PROTECTED);

        AdminUserEntity other = liveUser(8L, "ops_a");
        when(users.selectById(8L)).thenReturn(other);
        when(roles.countEnabledBuiltInByUserId(8L)).thenReturn(0);
        when(users.updateById(any(AdminUserEntity.class))).thenReturn(1);
        service.disable(8L);
        assertThat(other.getStatus()).isEqualTo("DISABLED");
        verify(sessions).logoutAllAdmin(8L);
    }

    @Test
    void updateReplacesRolesAndResetPasswordInvalidatesSessions() {
        AdminUserEntity other = liveUser(8L, "ops_a");
        when(users.selectById(8L)).thenReturn(other);
        RoleEntity role = new RoleEntity();
        role.setId(2L);
        when(roles.selectById(2L)).thenReturn(role);
        when(users.updateById(any(AdminUserEntity.class))).thenReturn(1);
        service.update(8L, new AdminUserUpdateCommand("新昵称", List.of(2L)));
        verify(userRoles).deleteByUserId(8L);
        verify(userRoles).insert(8L, 2L);

        service.resetPassword(8L, new AdminUserResetPasswordCommand("Newpass12!x"));
        assertThat(other.getMustChangePassword()).isEqualTo(1);
        verify(sessions).logoutAllAdmin(8L);

        service.delete(8L);
        assertThat(other.getDeleted()).isEqualTo(1);
        verify(sessions, Mockito.times(2)).logoutAllAdmin(8L);
    }

    @Test
    void pageProjectsRoleCodes() {
        AdminUserEntity row = liveUser(8L, "ops_a");
        when(users.countPage(null, null, null, null)).thenReturn(1L);
        when(users.listPage(null, null, null, null, 0L, 20)).thenReturn(List.of(row));
        when(users.listRoleCodes(8L)).thenReturn(List.of("ops"));
        var page = service.page(new AdminUserQuery(null, null, null, null, PageQuery.of(1, 20)));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).roles()).containsExactly("ops");
    }

    @Test
    void seedAdminCannotBeStrippedThenDeleted() {
        AdminUserEntity seed = liveUser(1L, "admin");
        when(users.selectById(1L)).thenReturn(seed);
        RoleEntity ops = new RoleEntity();
        ops.setId(2L);
        ops.setBuiltIn(0);
        when(roles.selectById(2L)).thenReturn(ops);
        assertThatThrownBy(() -> service.update(1L, new AdminUserUpdateCommand("超管", List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.USER_SELF_PROTECTED);
        verify(users, never()).updateById(any(AdminUserEntity.class));

        when(roles.countEnabledBuiltInByUserId(1L)).thenReturn(0);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.USER_SELF_PROTECTED);
        verify(sessions, never()).logoutAllAdmin(1L);

        RoleEntity superAdmin = new RoleEntity();
        superAdmin.setId(1L);
        superAdmin.setBuiltIn(1);
        when(roles.selectById(1L)).thenReturn(superAdmin);
        when(users.updateById(any(AdminUserEntity.class))).thenReturn(1);
        service.update(1L, new AdminUserUpdateCommand("超级管理员", List.of(1L)));
        verify(userRoles).deleteByUserId(1L);
        verify(userRoles).insert(1L, 1L);
    }

    @Test
    void weakPasswordRejected() {
        assertThatThrownBy(() -> service.create(new AdminUserCreateCommand("ops_b", "运营", "weak", List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.PASSWORD_POLICY_VIOLATED);
    }

    private static AdminUserEntity liveUser(long id, String username) {
        AdminUserEntity entity = new AdminUserEntity();
        entity.setId(id);
        entity.setUsername(username);
        entity.setNickname(username);
        entity.setStatus("ENABLED");
        entity.setDeleted(0);
        entity.setPasswordHash("hash");
        return entity;
    }
}
