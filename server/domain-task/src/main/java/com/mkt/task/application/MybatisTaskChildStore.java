package com.mkt.task.application;

import com.mkt.task.entity.TaskPlatformActionEntity;
import com.mkt.task.entity.TaskStepEntity;
import com.mkt.task.entity.TaskStepPlatformActionEntity;
import com.mkt.task.entity.TaskStepTransitionEntity;
import com.mkt.task.mapper.TaskPlatformActionMapper;
import com.mkt.task.mapper.TaskStepMapper;
import com.mkt.task.mapper.TaskStepPlatformActionMapper;
import com.mkt.task.mapper.TaskStepTransitionMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTaskChildStore implements TaskChildStore {

    private final TaskStepMapper steps;
    private final TaskStepTransitionMapper transitions;
    private final TaskPlatformActionMapper taskActions;
    private final TaskStepPlatformActionMapper stepActions;

    public MybatisTaskChildStore(
            TaskStepMapper steps,
            TaskStepTransitionMapper transitions,
            TaskPlatformActionMapper taskActions,
            TaskStepPlatformActionMapper stepActions) {
        this.steps = steps;
        this.transitions = transitions;
        this.taskActions = taskActions;
        this.stepActions = stepActions;
    }

    @Override
    public int insertStep(TaskStepEntity entity) {
        return steps.insert(entity);
    }

    @Override
    public int insertTransition(TaskStepTransitionEntity entity) {
        return transitions.insert(entity);
    }

    @Override
    public int insertTaskAction(TaskPlatformActionEntity entity) {
        return taskActions.insert(entity);
    }

    @Override
    public int insertStepAction(TaskStepPlatformActionEntity entity) {
        return stepActions.insert(entity);
    }

    @Override
    public List<TaskStepEntity> listSteps(long taskId) {
        return steps.selectByTaskId(taskId);
    }

    @Override
    public List<TaskStepTransitionEntity> listTransitions(long taskId) {
        return transitions.selectByTaskId(taskId);
    }

    @Override
    public List<TaskPlatformActionEntity> listTaskActions(long taskId) {
        return taskActions.selectByTaskId(taskId);
    }

    @Override
    public List<TaskStepPlatformActionEntity> listStepActions(List<Long> stepIds) {
        if (stepIds == null || stepIds.isEmpty()) {
            return List.of();
        }
        return stepActions.selectByStepIds(stepIds);
    }

    @Override
    public void replaceChildren(
            long taskId,
            List<TaskStepEntity> newSteps,
            List<TaskStepTransitionEntity> newTransitions,
            List<TaskPlatformActionEntity> newTaskActions,
            List<TaskStepPlatformActionEntity> newStepActions) {
        List<TaskStepEntity> existing = steps.selectByTaskId(taskId);
        List<Long> stepIds = existing.stream().map(TaskStepEntity::getId).toList();
        if (!stepIds.isEmpty()) {
            stepActions.deleteByStepIds(stepIds);
        }
        transitions.deleteByTaskId(taskId);
        taskActions.deleteByTaskId(taskId);
        steps.deleteByTaskId(taskId);
        persistChildren(newSteps, newTransitions, newTaskActions, newStepActions);
    }

    @Override
    public void persistChildren(
            List<TaskStepEntity> newSteps,
            List<TaskStepTransitionEntity> newTransitions,
            List<TaskPlatformActionEntity> newTaskActions,
            List<TaskStepPlatformActionEntity> newStepActions) {
        for (TaskStepEntity step : newSteps) {
            steps.insert(step);
        }
        for (TaskStepTransitionEntity edge : newTransitions) {
            transitions.insert(edge);
        }
        for (TaskPlatformActionEntity action : newTaskActions) {
            taskActions.insert(action);
        }
        for (TaskStepPlatformActionEntity action : newStepActions) {
            stepActions.insert(action);
        }
    }
}
