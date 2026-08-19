package com.mkt.identity.application;

import com.mkt.identity.command.AdminUserCreateCommand;
import com.mkt.identity.command.AdminUserResetPasswordCommand;
import com.mkt.identity.command.AdminUserUpdateCommand;
import com.mkt.identity.convert.AdminUserConvert;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.PasswordPolicies;
import com.mkt.identity.domain.SqlLikes;
import com.mkt.identity.domain.UserStatuses;
import com.mkt.identity.domain.Usernames;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.AdminUserMapper;
import com.mkt.identity.mapper.AdminUserRoleMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.identity.query.AdminUserQuery;
import com.mkt.identity.response.AdminUserView;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserAppService {

    private final AdminUserMapper users;
    private final AdminUserRoleMapper userRoles;
    private final RoleMapper roles;
    private final PasswordHasher hasher;
    private final SessionService sessions;
    private final RbacPermissionCache permissionCache;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    public AdminUserAppService(
            AdminUserMapper users,
            AdminUserRoleMapper userRoles,
            RoleMapper roles,
            PasswordHasher hasher,
            SessionService sessions,
            RbacPermissionCache permissionCache,
            IdentityAuditAppender audits,
            Clock clock) {
        this.users = users;
        this.userRoles = userRoles;
        this.roles = roles;
        this.hasher = hasher;
        this.sessions = sessions;
        this.permissionCache = permissionCache;
        this.audits = audits;
        this.clock = clock;
    }

    public PageData<AdminUserView> page(AdminUserQuery query) {
        String username = SqlLikes.containsOrNull(query.username());
        String nickname = SqlLikes.containsOrNull(query.nickname());
        String status = blankToNull(query.status());
        if (status != null && !UserStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "用户状态不合法");
        }
        long total = users.countPage(username, nickname, status, query.roleId());
        List<AdminUserEntity> rows =
                users.listPage(username, nickname, status, query.roleId(), query.page().offset(), query.page().pageSize());
        List<AdminUserView> views = new ArrayList<>(rows == null ? 0 : rows.size());
        if (rows != null) {
            for (AdminUserEntity row : rows) {
                views.add(AdminUserConvert.toView(row, users.listRoleCodes(row.getId())));
            }
        }
        return new PageData<>(total, views);
    }

    @Transactional
    public AdminUserEntity create(AdminUserCreateCommand command) {
        String username = Usernames.requireValid(command.username());
        if (username == null) {
            throw new BusinessException(AuthErrorCodes.USERNAME_INVALID_FORMAT);
        }
        if (!PasswordPolicies.adminSatisfied(command.password())) {
            throw new BusinessException(AuthErrorCodes.PASSWORD_POLICY_VIOLATED);
        }
        List<Long> roleIds = uniqueRoleIds(command.roleIds());
        AdminUserEntity entity = new AdminUserEntity();
        entity.setUsername(username);
        entity.setNickname(command.nickname().trim());
        entity.setPasswordHash(hasher.hash(command.password()));
        entity.setStatus(UserStatuses.ENABLED);
        entity.setDeleted(0);
        entity.setFailedAttempts(0);
        entity.setMustChangePassword(0);
        LocalDateTime now = IdentityTime.toUtc(clock.instant());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            users.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(AuthErrorCodes.USERNAME_DUPLICATE, ex);
        }
        replaceRoles(entity.getId(), roleIds);
        audits.append(
                "admin-user-create",
                "sys_admin_user",
                String.valueOf(entity.getId()),
                "SUCCESS",
                summary("create", username));
        return entity;
    }

    @Transactional
    public void update(long id, AdminUserUpdateCommand command) {
        AdminUserEntity existing = requireActive(id);
        List<Long> roleIds = uniqueRoleIds(command.roleIds());
        rejectStrippingSeedBuiltIn(existing, roleIds);
        existing.setNickname(command.nickname().trim());
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        replaceRoles(id, roleIds);
        permissionCache.evictUserAfterCommit(id);
        audits.append(
                "admin-user-update",
                "sys_admin_user",
                String.valueOf(id),
                "SUCCESS",
                summary("update", existing.getUsername()));
    }

    @Transactional
    public void disable(long id) {
        AdminUserEntity existing = requireActive(id);
        rejectProtected(existing);
        existing.setStatus(UserStatuses.DISABLED);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        sessions.logoutAllAdmin(id);
        audits.append(
                "admin-user-disable",
                "sys_admin_user",
                String.valueOf(id),
                "SUCCESS",
                summary("disable", existing.getUsername()));
    }

    @Transactional
    public void enable(long id) {
        AdminUserEntity existing = requireActive(id);
        rejectProtected(existing);
        existing.setStatus(UserStatuses.ENABLED);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        audits.append(
                "admin-user-enable",
                "sys_admin_user",
                String.valueOf(id),
                "SUCCESS",
                summary("enable", existing.getUsername()));
    }

    @Transactional
    public void resetPassword(long id, AdminUserResetPasswordCommand command) {
        AdminUserEntity existing = requireActive(id);
        if (!PasswordPolicies.adminSatisfied(command.newPassword())) {
            throw new BusinessException(AuthErrorCodes.PASSWORD_POLICY_VIOLATED);
        }
        existing.setPasswordHash(hasher.hash(command.newPassword()));
        existing.setMustChangePassword(1);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        sessions.logoutAllAdmin(id);
        audits.append(
                "admin-user-reset-password",
                "sys_admin_user",
                String.valueOf(id),
                "SUCCESS",
                summary("reset-password", existing.getUsername()));
    }

    @Transactional
    public void delete(long id) {
        AdminUserEntity existing = requireActive(id);
        rejectProtected(existing);
        existing.setDeleted(1);
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        users.updateById(existing);
        sessions.logoutAllAdmin(id);
        permissionCache.evictUserAfterCommit(id);
        audits.append(
                "admin-user-delete",
                "sys_admin_user",
                String.valueOf(id),
                "SUCCESS",
                summary("delete", existing.getUsername()));
    }

    private AdminUserEntity requireActive(long id) {
        AdminUserEntity existing = users.selectById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private void rejectProtected(AdminUserEntity existing) {
        UserPrincipal principal = UserContext.current().orElse(null);
        if (principal != null && principal.userId() != null && principal.userId().equals(existing.getId())) {
            throw new BusinessException(AuthErrorCodes.USER_SELF_PROTECTED);
        }
        if (isSeedAdmin(existing) || roles.countEnabledBuiltInByUserId(existing.getId()) > 0) {
            throw new BusinessException(AuthErrorCodes.USER_SELF_PROTECTED);
        }
    }

    private void rejectStrippingSeedBuiltIn(AdminUserEntity existing, List<Long> roleIds) {
        if (!isSeedAdmin(existing)) {
            return;
        }
        for (Long roleId : roleIds) {
            RoleEntity role = roles.selectById(roleId);
            if (role != null && role.builtInFlag()) {
                return;
            }
        }
        throw new BusinessException(AuthErrorCodes.USER_SELF_PROTECTED);
    }

    private static boolean isSeedAdmin(AdminUserEntity existing) {
        return "admin".equals(existing.getUsername());
    }

    private List<Long> uniqueRoleIds(List<Long> raw) {
        List<Long> ids = raw == null ? List.of() : raw;
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "角色 id 重复");
        }
        for (Long roleId : unique) {
            if (roleId == null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "角色 id 不合法");
            }
            RoleEntity role = roles.selectById(roleId);
            if (role == null) {
                throw new BusinessException(CommonErrorCodes.NOT_FOUND);
            }
        }
        return List.copyOf(unique);
    }

    private void replaceRoles(long userId, List<Long> roleIds) {
        userRoles.deleteByUserId(userId);
        for (Long roleId : roleIds) {
            userRoles.insert(userId, roleId);
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String summary(String action, String username) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("username", username);
        return JsonUtil.toJson(body);
    }
}
