package com.mkt.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mkt.identity.entity.SysConfigEntity;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MybatisConfigServiceTest {

    @Test
    void readsIntFallsBackOnMissingDisabledOrJunk() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        TwoLevelPlatformCache cache = new TwoLevelPlatformCache(new MemoryKeyValueStore());
        MybatisConfigService service = new MybatisConfigService(mapper, cache);
        when(mapper.getByKey("k")).thenReturn(null);
        assertThat(service.getInt("k", 7)).isEqualTo(7);

        SysConfigEntity enabled = new SysConfigEntity();
        enabled.setConfigValue("9");
        enabled.setStatus("ENABLED");
        when(mapper.getByKey("k")).thenReturn(enabled);
        assertThat(service.getInt("k", 7)).isEqualTo(9);

        enabled.setStatus("DISABLED");
        cache.evict(CacheNamespace.CONFIG, "k");
        assertThat(service.getInt("k", 7)).isEqualTo(7);

        enabled.setStatus("ENABLED");
        enabled.setConfigValue("nope");
        cache.evict(CacheNamespace.CONFIG, "k");
        assertThat(service.getInt("k", 7)).isEqualTo(7);
    }
}
