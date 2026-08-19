package com.mkt.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mkt.identity.entity.SysConfigEntity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MybatisConfigServiceTest {

    @Test
    void readsIntFallsBackOnMissingDisabledOrJunk() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        MybatisConfigService service = new MybatisConfigService(mapper);
        when(mapper.selectOne(any())).thenReturn(null);
        assertThat(service.getInt("k", 7)).isEqualTo(7);

        SysConfigEntity enabled = new SysConfigEntity();
        enabled.setConfigValue("9");
        enabled.setStatus("ENABLED");
        when(mapper.selectOne(any())).thenReturn(enabled);
        assertThat(service.getInt("k", 7)).isEqualTo(9);

        enabled.setStatus("DISABLED");
        assertThat(service.getInt("k", 7)).isEqualTo(7);

        enabled.setStatus("ENABLED");
        enabled.setConfigValue("nope");
        assertThat(service.getInt("k", 7)).isEqualTo(7);
    }
}
