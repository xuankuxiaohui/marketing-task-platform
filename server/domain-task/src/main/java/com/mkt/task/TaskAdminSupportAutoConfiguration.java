package com.mkt.task;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.task.application.TaskInstanceAppService;
import com.mkt.task.application.TaskProgressReportStore;
import com.mkt.task.application.TaskPublishAppService;
import com.mkt.task.schedule.InstanceExpireScheduler;
import com.mkt.task.schedule.ProgressCleanScheduler;
import com.mkt.task.schedule.PublishScanScheduler;
import com.mkt.task.support.AlertWebhook;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Admin-only beans kept off {@code @ComponentScan} (Boot 4 REGISTER_BEAN). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
public class TaskAdminSupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AlertWebhook alertWebhook(@Value("${mkt.alert.webhook-url:}") String url) {
        return new AlertWebhook(url);
    }

    @Bean
    @ConditionalOnBean({TaskPublishAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    PublishScanScheduler publishScanScheduler(TaskPublishAppService publishes, PlatformLock locks, Clock clock) {
        return new PublishScanScheduler(publishes, locks, clock);
    }

    @Bean
    @ConditionalOnBean({TaskProgressReportStore.class, PlatformLock.class})
    @ConditionalOnMissingBean
    ProgressCleanScheduler progressCleanScheduler(
            TaskProgressReportStore reports, PlatformLock locks, Clock clock) {
        return new ProgressCleanScheduler(reports, locks, clock);
    }

    @Bean
    @ConditionalOnBean({TaskInstanceAppService.class, PlatformLock.class})
    @ConditionalOnMissingBean
    InstanceExpireScheduler instanceExpireScheduler(
            TaskInstanceAppService instances, PlatformLock locks, Clock clock) {
        return new InstanceExpireScheduler(instances, locks, clock);
    }
}
