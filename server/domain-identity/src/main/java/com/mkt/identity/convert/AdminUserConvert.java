package com.mkt.identity.convert;

import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.response.AdminUserView;
import java.util.List;

public final class AdminUserConvert {

    private AdminUserConvert() {}

    public static AdminUserView toView(AdminUserEntity entity, List<String> roles) {
        return new AdminUserView(
                entity.getId(),
                entity.getUsername(),
                entity.getNickname(),
                entity.getStatus(),
                roles,
                IdentityTime.toInstant(entity.getLastLoginAt()),
                IdentityTime.toInstant(entity.getCreatedAt()));
    }
}
