package com.mkt.identity.application;

import com.mkt.identity.domain.MenuTrees;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.response.AdminProfileResponse;
import com.mkt.identity.response.MenuNodeResponse;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminMenuService {

    private final AdminUserStore users;
    private final PermissionMapper permissions;
    private final RbacPermissionCache cache;

    public AdminMenuService(AdminUserStore users, PermissionMapper permissions, RbacPermissionCache cache) {
        this.users = users;
        this.permissions = permissions;
        this.cache = cache;
    }

    public List<MenuNodeResponse> menus(long userId) {
        List<Long> held = permissions.listHeldIdsByUserId(userId);
        return MenuTrees.authorizedMenus(permissions.listAllOrdered(), new HashSet<>(held == null ? List.of() : held));
    }

    public AdminProfileResponse profile(long userId) {
        AdminUserEntity user = users.getById(userId);
        if (user == null || user.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return new AdminProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                users.listRoleCodes(userId),
                cache.codesFor(userId),
                user.mustChangePasswordFlag());
    }
}
