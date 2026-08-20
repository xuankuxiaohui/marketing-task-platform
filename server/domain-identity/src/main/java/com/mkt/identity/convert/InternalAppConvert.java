package com.mkt.identity.convert;

import com.mkt.identity.entity.InternalAppEntity;
import com.mkt.identity.response.InternalAppView;

public final class InternalAppConvert {

    private InternalAppConvert() {}

    public static InternalAppView toView(InternalAppEntity entity) {
        return new InternalAppView(
                entity.getId(),
                entity.getAppId(),
                entity.getAppName(),
                entity.getStatus(),
                IdentityTime.toInstant(entity.getPrevExpireAt()),
                IdentityTime.toInstant(entity.getCreatedAt()));
    }
}
