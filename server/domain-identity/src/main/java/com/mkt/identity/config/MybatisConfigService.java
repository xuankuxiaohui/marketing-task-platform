package com.mkt.identity.config;

import com.mkt.identity.entity.SysConfigEntity;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import org.springframework.stereotype.Service;

@Service
public class MybatisConfigService implements ConfigService {

    private final SysConfigMapper mapper;
    private final PlatformCache cache;

    public MybatisConfigService(SysConfigMapper mapper, PlatformCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String raw = cache.get(CacheNamespace.CONFIG, key, String.class, () -> loadEnabledValue(key));
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String loadEnabledValue(String key) {
        SysConfigEntity row = mapper.getByKey(key);
        if (row == null || row.getConfigValue() == null || row.getConfigValue().isBlank()) {
            return null;
        }
        if (!row.enabled()) {
            return null;
        }
        return row.getConfigValue();
    }
}

