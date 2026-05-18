package com.marketing.task.service.platform;

import com.marketing.task.context.UserContext;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.enums.Platform;

public interface PlatformAdapter {
    Platform platform();

    default TaskStepPlatform renderStep(TaskStep step, TaskStepPlatform platformConfig, UserContext userContext) {
        return platformConfig;
    }
}
