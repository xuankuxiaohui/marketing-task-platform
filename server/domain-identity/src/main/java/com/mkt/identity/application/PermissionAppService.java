package com.mkt.identity.application;

import com.mkt.identity.command.PermissionSaveCommand;
import com.mkt.identity.convert.IdentityTime;
import com.mkt.identity.domain.MenuTrees;
import com.mkt.identity.domain.PermissionCodes;
import com.mkt.identity.domain.PermissionTypes;
import com.mkt.identity.domain.RoleCodes;
import com.mkt.identity.domain.RoleStatuses;
import com.mkt.identity.entity.PermissionEntity;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.identity.mapper.RolePermissionMapper;
import com.mkt.identity.response.PermissionTreeNodeResponse;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.json.JsonUtil;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionAppService {

    private final PermissionMapper permissions;
    private final RoleMapper roles;
    private final RolePermissionMapper rolePermissions;
    private final RbacPermissionCache cache;
    private final IdentityAuditAppender audits;
    private final Clock clock;

    public PermissionAppService(
            PermissionMapper permissions,
            RoleMapper roles,
            RolePermissionMapper rolePermissions,
            RbacPermissionCache cache,
            IdentityAuditAppender audits,
            Clock clock) {
        this.permissions = permissions;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.cache = cache;
        this.audits = audits;
        this.clock = clock;
    }

    public List<PermissionTreeNodeResponse> tree() {
        return MenuTrees.fullTree(permissions.listAllOrdered());
    }

    @Transactional
    public PermissionEntity create(PermissionSaveCommand command) {
        PermissionEntity entity = new PermissionEntity();
        apply(command, entity, true);
        entity.setCreatedAt(IdentityTime.toUtc(clock.instant()));
        try {
            permissions.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "权限编码已存在", ex);
        }
        bindBuiltIn(entity.getId());
        cache.evictAllAfterCommit();
        audits.append(
                "permission-create",
                "sys_permission",
                String.valueOf(entity.getId()),
                "SUCCESS",
                summary("create", entity));
        return entity;
    }

    @Transactional
    public void update(long id, PermissionSaveCommand command) {
        PermissionEntity existing = require(id);
        if (command.type() != null && !command.type().equals(existing.getType())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "权限类型不可修改");
        }
        apply(command, existing, false);
        permissions.updateById(existing);
        cache.evictAllAfterCommit();
        audits.append("permission-update", "sys_permission", String.valueOf(id), "SUCCESS", summary("update", existing));
    }

    @Transactional
    public void delete(long id) {
        PermissionEntity existing = require(id);
        if (permissions.countByParentId(id) > 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "存在子节点，无法删除");
        }
        rolePermissions.deleteByPermissionId(id);
        permissions.deleteById(id);
        cache.evictAllAfterCommit();
        audits.append(
                "permission-delete", "sys_permission", String.valueOf(id), "SUCCESS", summary("delete", existing));
    }

    private void apply(PermissionSaveCommand command, PermissionEntity entity, boolean creating) {
        String type = command.type();
        if (!PermissionTypes.valid(type)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "权限类型不合法");
        }
        long parentId = command.parentId() == null ? 0L : command.parentId();
        if (parentId < 0L) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "父节点不合法");
        }
        if (PermissionTypes.isOperation(type)) {
            if (parentId == 0L) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "操作权限必须挂在菜单节点下");
            }
            PermissionEntity parent = permissions.selectById(parentId);
            if (parent == null || !PermissionTypes.isMenu(parent.getType())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "操作权限必须挂在菜单节点下");
            }
            if (!PermissionCodes.isOperationCode(command.code())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "操作权限编码格式必须为域:资源:操作");
            }
            entity.setCode(command.code());
        } else {
            if (parentId != 0L) {
                PermissionEntity parent = permissions.selectById(parentId);
                if (parent == null || !PermissionTypes.isMenu(parent.getType())) {
                    throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "父节点必须是菜单");
                }
            }
            if (command.code() != null && !command.code().isBlank()) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "菜单节点编码必须为空");
            }
            entity.setCode(null);
        }
        if (creating) {
            entity.setType(type);
            entity.setStatus(RoleStatuses.ENABLED);
        }
        entity.setParentId(parentId);
        entity.setName(command.name().trim());
        entity.setRoute(blankToNull(command.route()));
        entity.setComponent(blankToNull(command.component()));
        entity.setIcon(blankToNull(command.icon()));
        entity.setSort(command.sort() == null ? 0 : command.sort());
    }

    private void bindBuiltIn(long permissionId) {
        RoleEntity superAdmin = roles.getByCode(RoleCodes.SUPER_ADMIN);
        if (superAdmin == null) {
            return;
        }
        rolePermissions.insert(superAdmin.getId(), permissionId);
    }

    private PermissionEntity require(long id) {
        PermissionEntity existing = permissions.selectById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String summary(String action, PermissionEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", action);
        body.put("type", entity.getType());
        body.put("code", entity.getCode() == null ? "" : entity.getCode());
        body.put("name", entity.getName());
        return JsonUtil.toJson(body);
    }
}
