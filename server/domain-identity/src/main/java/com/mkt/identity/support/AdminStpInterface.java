package com.mkt.identity.support;

import cn.dev33.satoken.stp.StpInterface;
import com.mkt.identity.application.AdminUserStore;
import com.mkt.infra.session.StpAdmin;
import java.util.List;

/** Permission/role source for {@code @SaCheckPermission} on admin-app. */
public final class AdminStpInterface implements StpInterface {

    private final AdminUserStore users;

    public AdminStpInterface(AdminUserStore users) {
        this.users = users;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!StpAdmin.TYPE.equals(loginType) || loginId == null) {
            return List.of();
        }
        return users.listPermissionCodes(Long.parseLong(String.valueOf(loginId)));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!StpAdmin.TYPE.equals(loginType) || loginId == null) {
            return List.of();
        }
        return users.listRoleCodes(Long.parseLong(String.valueOf(loginId)));
    }
}
