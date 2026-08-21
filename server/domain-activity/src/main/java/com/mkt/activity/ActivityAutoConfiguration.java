package com.mkt.activity;

import com.mkt.activity.support.ActivitySettings;
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
@Import(ActivityPersistenceScan.class)
public class ActivityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ActivitySettings activitySettings() {
        return new ActivitySettings();
    }
}
