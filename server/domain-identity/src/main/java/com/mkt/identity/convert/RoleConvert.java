package com.mkt.identity.convert;

import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.response.RoleView;

public final class RoleConvert {

    private RoleConvert() {}

    public static RoleView toView(RoleEntity entity, long userCount) {
        return new RoleView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                userCount,
                IdentityTime.toInstant(entity.getCreatedAt()));
    }
}
