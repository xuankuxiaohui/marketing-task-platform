package com.mkt.reward;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.GrantStepResumer;
import com.mkt.reward.schedule.GrantRetryScheduler;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Admin-only beans kept off {@code @ComponentScan} (Boot 4 REGISTER_BEAN). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
public class RewardAdminSupportAutoConfiguration {

    @Bean
    @ConditionalOnBean({GrantAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    GrantRetryScheduler grantRetryScheduler(
            GrantAppService grants, ObjectProvider<GrantStepResumer> resumer, PlatformLock locks, Clock clock) {
        return new GrantRetryScheduler(grants, resumer.getIfAvailable(), locks, clock);
    }
}
