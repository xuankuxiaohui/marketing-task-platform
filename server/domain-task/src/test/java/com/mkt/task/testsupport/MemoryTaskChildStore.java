package com.mkt.task.testsupport;

import com.mkt.task.application.TaskChildStore;
import com.mkt.task.entity.TaskPlatformActionEntity;
import com.mkt.task.entity.TaskStepEntity;
import com.mkt.task.entity.TaskStepPlatformActionEntity;
import com.mkt.task.entity.TaskStepTransitionEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryTaskChildStore implements TaskChildStore {

    public final List<TaskStepEntity> steps = new CopyOnWriteArrayList<>();
    public final List<TaskStepTransitionEntity> transitions = new CopyOnWriteArrayList<>();
    public final List<TaskPlatformActionEntity> taskActions = new CopyOnWriteArrayList<>();
    public final List<TaskStepPlatformActionEntity> stepActions = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);
    public volatile RuntimeException failOnStepInsert;

    @Override
    public int insertStep(TaskStepEntity entity) {
        if (failOnStepInsert != null) {
            throw failOnStepInsert;
        }
        entity.setId(seq.getAndIncrement());
        steps.add(entity);
        return 1;
    }

    @Override
    public int insertTransition(TaskStepTransitionEntity entity) {
        entity.setId(seq.getAndIncrement());
        transitions.add(entity);
        return 1;
    }

    @Override
    public int insertTaskAction(TaskPlatformActionEntity entity) {
        entity.setId(seq.getAndIncrement());
        taskActions.add(entity);
        return 1;
    }

    @Override
    public int insertStepAction(TaskStepPlatformActionEntity entity) {
        entity.setId(seq.getAndIncrement());
        stepActions.add(entity);
        return 1;
    }

    @Override
    public List<TaskStepEntity> listSteps(long taskId) {
        return steps.stream().filter(row -> row.getTaskId() == taskId).toList();
    }

    @Override
    public List<TaskStepTransitionEntity> listTransitions(long taskId) {
        return transitions.stream().filter(row -> row.getTaskId() == taskId).toList();
    }

    @Override
    public List<TaskPlatformActionEntity> listTaskActions(long taskId) {
        return taskActions.stream().filter(row -> row.getTaskId() == taskId).toList();
    }

    @Override
    public List<TaskStepPlatformActionEntity> listStepActions(List<Long> stepIds) {
        if (stepIds == null || stepIds.isEmpty()) {
            return List.of();
        }
        return stepActions.stream().filter(row -> stepIds.contains(row.getStepId())).toList();
    }

    @Override
    public void persistChildren(
            List<TaskStepEntity> newSteps,
            List<TaskStepTransitionEntity> newTransitions,
            List<TaskPlatformActionEntity> newTaskActions,
            List<TaskStepPlatformActionEntity> newStepActions) {
        for (TaskStepEntity step : newSteps) {
            insertStep(step);
        }
        for (TaskStepTransitionEntity edge : newTransitions) {
            insertTransition(edge);
        }
        for (TaskPlatformActionEntity action : newTaskActions) {
            insertTaskAction(action);
        }
        for (TaskStepPlatformActionEntity action : newStepActions) {
            insertStepAction(action);
        }
    }

    @Override
    public void replaceChildren(
            long taskId,
            List<TaskStepEntity> newSteps,
            List<TaskStepTransitionEntity> newTransitions,
            List<TaskPlatformActionEntity> newTaskActions,
            List<TaskStepPlatformActionEntity> newStepActions) {
        List<Long> stepIds = new ArrayList<>();
        steps.removeIf(row -> {
            if (row.getTaskId() == taskId) {
                stepIds.add(row.getId());
                return true;
            }
            return false;
        });
        stepActions.removeIf(row -> stepIds.contains(row.getStepId()));
        transitions.removeIf(row -> row.getTaskId() == taskId);
        taskActions.removeIf(row -> row.getTaskId() == taskId);
        persistChildren(newSteps, newTransitions, newTaskActions, newStepActions);
    }
}
