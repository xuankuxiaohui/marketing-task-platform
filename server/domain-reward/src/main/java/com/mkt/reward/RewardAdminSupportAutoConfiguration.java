package com.mkt.reward;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.reward.application.ClaimAppService;
import com.mkt.reward.application.FulfillmentService;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.GrantStepResumer;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.schedule.ClaimTimeoutScheduler;
import com.mkt.reward.schedule.FulfillRetryScheduler;
import com.mkt.reward.schedule.GrantRetryScheduler;
import com.mkt.reward.schedule.PointsExpireScheduler;
import com.mkt.reward.schedule.PrizeExpireScheduler;
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

    @Bean
    @ConditionalOnBean({ClaimAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    ClaimTimeoutScheduler claimTimeoutScheduler(ClaimAppService claims, PlatformLock locks, Clock clock) {
        return new ClaimTimeoutScheduler(claims, locks, clock);
    }

    @Bean
    @ConditionalOnBean({ClaimAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    PrizeExpireScheduler prizeExpireScheduler(ClaimAppService claims, PlatformLock locks, Clock clock) {
        return new PrizeExpireScheduler(claims, locks, clock);
    }

    @Bean
    @ConditionalOnBean({FulfillmentService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    FulfillRetryScheduler fulfillRetryScheduler(FulfillmentService fulfillment, PlatformLock locks, Clock clock) {
        return new FulfillRetryScheduler(fulfillment, locks, clock);
    }

    @Bean
    @ConditionalOnBean({PointsAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    PointsExpireScheduler pointsExpireScheduler(PointsAppService points, PlatformLock locks, Clock clock) {
        return new PointsExpireScheduler(points, locks, clock);
    }
}
