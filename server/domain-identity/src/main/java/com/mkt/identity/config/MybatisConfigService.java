package com.mkt.identity.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkt.identity.entity.SysConfigEntity;
import org.springframework.stereotype.Service;

@Service
public class MybatisConfigService implements ConfigService {

    private final SysConfigMapper mapper;

    public MybatisConfigService(SysConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        SysConfigEntity row = mapper.selectOne(
                new LambdaQueryWrapper<SysConfigEntity>().eq(SysConfigEntity::getConfigKey, key));
        if (row == null || row.getConfigValue() == null || row.getConfigValue().isBlank()) {
            return defaultValue;
        }
        if (row.getStatus() != null && !"ENABLED".equals(row.getStatus())) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(row.getConfigValue().trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
