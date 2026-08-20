package com.mkt.identity.application;

import com.mkt.identity.domain.AdminPermissionCatalog;
import com.mkt.identity.domain.PermissionUnion;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.mapper.AdminUserMapper;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** {@code rbac:permission} user permission-code set (R2.6 / design §6.2). */
@Component
public class RbacPermissionCache {

    private final PlatformCache cache;
    private final AdminUserMapper users;
    private final RoleMapper roles;
    private final PermissionMapper permissions;

    public RbacPermissionCache(
            PlatformCache cache, AdminUserMapper users, RoleMapper roles, PermissionMapper permissions) {
        this.cache = cache;
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
    }

    public List<String> codesFor(long userId) {
        String[] cached = cache.get(
                CacheNamespace.RBAC_PERMISSION, String.valueOf(userId), String[].class, () -> load(userId));
        if (cached == null || cached.length == 0) {
            return List.of();
        }
        return List.of(cached);
    }

    public void evictUserAfterCommit(long userId) {
        cache.evictAfterCommit(CacheNamespace.RBAC_PERMISSION, String.valueOf(userId));
    }

    public void evictAllAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.evictNamespace(CacheNamespace.RBAC_PERMISSION);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evictNamespace(CacheNamespace.RBAC_PERMISSION);
            }
        });
    }

    String[] load(long userId) {
        List<RoleEntity> bound = roles.listByUserId(userId);
        List<PermissionUnion.RoleSlice> slices = new ArrayList<>(bound == null ? 0 : bound.size());
        if (bound != null) {
            for (RoleEntity role : bound) {
                slices.add(new PermissionUnion.RoleSlice(role.enabled(), role.builtInFlag(), Set.of()));
            }
        }
        List<String> fromDb = users.listPermissionCodes(userId);
        Set<String> union = new LinkedHashSet<>(fromDb == null ? List.of() : fromDb);
        if (PermissionUnion.hasEnabledBuiltIn(slices)) {
            union.addAll(AdminPermissionCatalog.P0);
            List<String> extra = permissions.listEnabledOperationCodes();
            if (extra != null) {
                union.addAll(extra);
            }
        }
        return union.toArray(String[]::new);
    }
}
