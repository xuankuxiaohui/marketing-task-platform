package com.mkt.identity.convert;

import com.mkt.identity.domain.ConfigMasks;
import com.mkt.identity.entity.SysConfigEntity;
import com.mkt.identity.response.ConfigView;

public final class ConfigConvert {

    private ConfigConvert() {}

    public static ConfigView toView(SysConfigEntity entity) {
        boolean masked = entity.maskedFlag();
        String value = masked ? ConfigMasks.DISPLAY : entity.getConfigValue();
        return new ConfigView(
                entity.getId(),
                entity.getConfigKey(),
                entity.getConfigGroup(),
                value,
                entity.getValueType(),
                masked,
                entity.getStatus(),
                entity.getRemark(),
                IdentityTime.toInstant(entity.getUpdatedAt()));
    }
}
