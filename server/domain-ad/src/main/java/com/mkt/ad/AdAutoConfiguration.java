package com.mkt.ad;

import com.mkt.ad.support.AdSettings;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@AutoConfigureBefore(name = "com.mkt.identity.IdentityAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@Import(AdPersistenceScan.class)
public class AdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AdSettings adSettings() {
        return new AdSettings();
    }
}
