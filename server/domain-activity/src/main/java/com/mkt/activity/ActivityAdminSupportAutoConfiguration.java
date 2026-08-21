package com.mkt.activity;

import com.mkt.activity.application.ActivityAdminAppService;
import com.mkt.activity.schedule.ActivityPublishScanScheduler;
import com.mkt.infra.lock.PlatformLock;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
public class ActivityAdminSupportAutoConfiguration {

    @Bean
    @ConditionalOnBean({ActivityAdminAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    ActivityPublishScanScheduler activityPublishScanScheduler(
            ActivityAdminAppService admin, PlatformLock locks, Clock clock) {
        return new ActivityPublishScanScheduler(admin, locks, clock);
    }
}
