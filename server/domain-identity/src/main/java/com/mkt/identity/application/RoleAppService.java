package com.mkt.identity.application;

import com.mkt.identity.command.RoleAssignPermissionsCommand;
import com.mkt.identity.command.RoleCreateCommand;
import com.mkt.identity.command.RoleUpdateCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.convert.RoleConvert;
import com.mkt.identity.domain.RoleCodes;
import com.mkt.identity.domain.RoleStatuses;
import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.AdminUserRoleMapper;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.identity.mapper.RolePermissionMapper;
import com.mkt.identity.query.RoleQuery;
import com.mkt.identity.response.RoleView;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
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
public class RoleAppService {

    private final RoleMapper roles;
    private final AdminUserRoleMapper userRoles;
    private final RolePermissionMapper rolePermissions;
    private final PermissionMapper permissions;
    private final RbacPermissionCache cache;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    public RoleAppService(
            RoleMapper roles,
            AdminUserRoleMapper userRoles,
            RolePermissionMapper rolePermissions,
            PermissionMapper permissions,
            RbacPermissionCache cache,
            IdentityAuditAppender audits,
            Clock clock) {
        this.roles = roles;
        this.userRoles = userRoles;
        this.rolePermissions = rolePermissions;
        this.permissions = permissions;
        this.cache = cache;
        this.audits = audits;
        this.clock = clock;
    }

    public PageData<RoleView> page(RoleQuery query) {
        if (query.all()) {
            List<RoleEntity> rows = roles.listEnabled();
            return new PageData<>((long) rows.size(), toViews(rows == null ? List.of() : rows));
        }
        long total = roles.countAll();
        List<RoleEntity> rows = roles.listPage(query.page().offset(), query.page().pageSize());
        return new PageData<>(total, toViews(rows == null ? List.of() : rows));
    }

    @Transactional
    public RoleEntity create(RoleCreateCommand command) {
        String code = RoleCodes.normalizeOrNull(command.code());
        if (code == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "角色编码格式不正确");
        }
        RoleEntity entity = new RoleEntity();
        entity.setCode(code);
        entity.setName(command.name().trim());
        entity.setDescription(blankToNull(command.description()));
        entity.setStatus(RoleStatuses.ENABLED);
        entity.setBuiltIn(0);
        LocalDateTime now = IdentityTime.toUtc(clock.instant());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            roles.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "角色编码已存在", ex);
        }
        audits.append("role-create", "sys_role", String.valueOf(entity.getId()), "SUCCESS", summary("create", code));
        return entity;
    }

    @Transactional
    public void update(long id, RoleUpdateCommand command) {
        RoleEntity existing = require(id);
        if (!RoleStatuses.valid(command.status())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "角色状态不合法");
        }
        if (existing.builtInFlag() && RoleStatuses.DISABLED.equals(command.status())) {
            throw new BusinessException(AuthErrorCodes.ROLE_BUILT_IN);
        }
        String previousStatus = existing.getStatus();
        existing.setName(command.name().trim());
        existing.setDescription(blankToNull(command.description()));
        existing.setStatus(command.status());
        existing.setUpdatedAt(IdentityTime.toUtc(clock.instant()));
        roles.updateById(existing);
        if (!command.status().equals(previousStatus)) {
            cache.evictAllAfterCommit();
        }
        audits.append("role-update", "sys_role", String.valueOf(id), "SUCCESS", summary("update", existing.getCode()));
    }

    @Transactional
    public void delete(long id) {
        RoleEntity existing = require(id);
        if (existing.builtInFlag()) {
            throw new BusinessException(AuthErrorCodes.ROLE_BUILT_IN);
        }
        rolePermissions.deleteByRoleId(id);
        userRoles.deleteByRoleId(id);
        roles.deleteById(id);
        cache.evictAllAfterCommit();
        audits.append("role-delete", "sys_role", String.valueOf(id), "SUCCESS", summary("delete", existing.getCode()));
    }

    public List<Long> listPermissionIds(long id) {
        require(id);
        List<Long> ids = rolePermissions.listPermissionIds(id);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    @Transactional
    public void assignPermissions(long id, RoleAssignPermissionsCommand command) {
        RoleEntity existing = require(id);
        if (existing.builtInFlag()) {
            throw new BusinessException(AuthErrorCodes.ROLE_BUILT_IN);
        }
        List<Long> ids = command.permissionIds() == null ? List.of() : command.permissionIds();
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "权限 id 重复");
        }
        for (Long permissionId : unique) {
            if (permissionId == null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "权限 id 不合法");
            }
            PermissionEntity node = permissions.selectById(permissionId);
            if (node == null) {
                throw new BusinessException(CommonErrorCodes.NOT_FOUND);
            }
        }
        rolePermissions.deleteByRoleId(id);
        for (Long permissionId : unique) {
            rolePermissions.insert(id, permissionId);
        }
        cache.evictAllAfterCommit();
        audits.append(
                "role-assign-permission",
                "sys_role",
                String.valueOf(id),
                "SUCCESS",
                JsonUtil.toJson(Map.of("roleCode", existing.getCode(), "permissionIds", List.copyOf(unique))));
    }

    private RoleEntity require(long id) {
        RoleEntity existing = roles.selectById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private List<RoleView> toViews(List<RoleEntity> rows) {
        List<RoleView> views = new ArrayList<>(rows.size());
        for (RoleEntity row : rows) {
            views.add(RoleConvert.toView(row, userRoles.countUsers(row.getId())));
        }
        return views;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String summary(String action, String code) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("code", code);
        return JsonUtil.toJson(body);
    }
}
