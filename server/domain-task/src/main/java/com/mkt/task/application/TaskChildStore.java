package com.mkt.task.application;

import com.mkt.task.entity.TaskPlatformActionEntity;
import com.mkt.task.entity.TaskStepEntity;
import com.mkt.task.entity.TaskStepPlatformActionEntity;
import com.mkt.task.entity.TaskStepTransitionEntity;
import java.util.List;

public interface TaskChildStore {

    int insertStep(TaskStepEntity entity);

    int insertTransition(TaskStepTransitionEntity entity);

    int insertTaskAction(TaskPlatformActionEntity entity);

    int insertStepAction(TaskStepPlatformActionEntity entity);

    List<TaskStepEntity> listSteps(long taskId);

    List<TaskStepTransitionEntity> listTransitions(long taskId);

    List<TaskPlatformActionEntity> listTaskActions(long taskId);

    List<TaskStepPlatformActionEntity> listStepActions(List<Long> stepIds);

    void persistChildren(
            List<TaskStepEntity> steps,
            List<TaskStepTransitionEntity> transitions,
            List<TaskPlatformActionEntity> taskActions,
            List<TaskStepPlatformActionEntity> stepActions);

    void replaceChildren(
            long taskId,
            List<TaskStepEntity> steps,
            List<TaskStepTransitionEntity> transitions,
            List<TaskPlatformActionEntity> taskActions,
            List<TaskStepPlatformActionEntity> stepActions);
}
