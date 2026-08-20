package com.mkt.admin.bootstrap;

import com.mkt.contract.GrantSource;
import com.mkt.reward.application.GrantStepResumer;
import com.mkt.task.application.TaskStepAppService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires grant-retry success back into the step engine without a fourth write port. */
@Configuration
public class GrantResumeConfiguration {

    @Bean
    @ConditionalOnBean(TaskStepAppService.class)
    GrantStepResumer grantStepResumer(TaskStepAppService steps) {
        return (source, sourceId) -> {
            if (source != GrantSource.TASK_STEP) {
                return;
            }
            steps.resumeAfterGrant(sourceId);
        };
    }
}
