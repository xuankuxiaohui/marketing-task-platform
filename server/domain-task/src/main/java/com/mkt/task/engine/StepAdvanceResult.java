package com.mkt.task.engine;

import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.response.RewardFeedbackView;
import java.util.List;

public record StepAdvanceResult(
        TaskInstanceEntity instance,
        TaskInstanceStepEntity step,
        Integer progressTarget,
        List<RewardFeedbackView> rewardFeedback,
        boolean idempotent) {}
