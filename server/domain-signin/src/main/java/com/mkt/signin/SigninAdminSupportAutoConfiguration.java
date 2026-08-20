package com.mkt.signin;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.signin.application.SigninAdminAppService;
import com.mkt.signin.schedule.SigninPublishScanScheduler;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
public class SigninAdminSupportAutoConfiguration {

    @Bean
    @ConditionalOnBean({SigninAdminAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    SigninPublishScanScheduler signinPublishScanScheduler(
            SigninAdminAppService admin, PlatformLock locks, Clock clock) {
        return new SigninPublishScanScheduler(admin, locks, clock);
    }
}
