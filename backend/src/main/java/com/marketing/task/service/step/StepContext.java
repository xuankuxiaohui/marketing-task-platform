package com.marketing.task.service.step;

import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StepContext {
    private UserTaskInstance instance;
    private TaskStep step;
}
