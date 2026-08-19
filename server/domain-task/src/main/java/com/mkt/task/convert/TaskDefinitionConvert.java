package com.mkt.task.convert;

import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskPlatformActionEntity;
import com.mkt.task.entity.TaskStepEntity;
import com.mkt.task.entity.TaskStepPlatformActionEntity;
import com.mkt.task.entity.TaskStepTransitionEntity;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskDefinitionView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TaskDefinitionConvert {

    private TaskDefinitionConvert() {}

    public static void applyHeader(TaskDefinitionSaveCommand command, TaskDefinitionEntity entity) {
        entity.setCode(command.code());
        entity.setName(command.name());
        entity.setDescription(blankToNull(command.description()));
        entity.setCategory(blankToNull(command.category()));
        entity.setIconUrl(blankToNull(command.iconUrl()));
        entity.setBadgeText(blankToNull(command.badgeText()));
        entity.setStartTime(TaskTime.toUtc(command.startTime()));
        entity.setEndTime(TaskTime.toUtc(command.endTime()));
        entity.setSortWeight(command.sortWeight() == null ? 0 : command.sortWeight());
        entity.setCycleType(command.cycleType());
        entity.setCronExpr(blankToNull(command.cronExpr()));
        entity.setSpecialStart(TaskTime.toUtc(command.specialStart()));
        entity.setSpecialEnd(TaskTime.toUtc(command.specialEnd()));
        entity.setMutexGroupId(command.mutexGroupId());
        TaskGrayCommand gray = command.gray();
        if (gray == null) {
            entity.setGrayType("NONE");
            entity.setGrayRatio(null);
            entity.setGrayAbGroup(null);
            entity.setGrayCrowdId(null);
            entity.setGrayExcludeCrowdId(null);
        } else {
            entity.setGrayType(gray.type() == null ? "NONE" : gray.type());
            entity.setGrayRatio(gray.ratio());
            entity.setGrayAbGroup(blankToNull(gray.abGroup()));
            entity.setGrayCrowdId(gray.crowdId());
            entity.setGrayExcludeCrowdId(gray.excludeCrowdId());
        }
        TaskFilterCommand filter = command.filter();
        if (filter == null) {
            entity.setFilterExpr(null);
            entity.setFilterAllowCrowdIds(null);
            entity.setFilterExcludeCrowdIds(null);
        } else {
            entity.setFilterExpr(blankToNull(filter.expr()));
            entity.setFilterAllowCrowdIds(TaskJson.longs(filter.allowCrowdIds()));
            entity.setFilterExcludeCrowdIds(TaskJson.longs(filter.excludeCrowdIds()));
        }
    }

    public static TaskDefinitionView toView(TaskDefinitionEntity entity) {
        return new TaskDefinitionView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getVersion() == null ? 0 : entity.getVersion(),
                entity.getSortWeight() == null ? 0 : entity.getSortWeight(),
                TaskTime.toInstant(entity.getUpdatedAt()));
    }

    public static TaskDefinitionAggregateResponse toAggregate(
            TaskDefinitionEntity entity,
            List<TaskStepEntity> steps,
            List<TaskStepTransitionEntity> transitions,
            List<TaskPlatformActionEntity> taskActions,
            List<TaskStepPlatformActionEntity> stepActions) {
        Map<Long, String> codeById = new HashMap<>();
        List<TaskStepCommand> stepCmds = new ArrayList<>();
        for (TaskStepEntity step : steps) {
            codeById.put(step.getId(), step.getCode());
            stepCmds.add(new TaskStepCommand(
                    step.getCode(),
                    step.getName(),
                    step.getSeq(),
                    step.getType(),
                    step.getProgressTarget(),
                    step.getPrizeId()));
        }
        List<TaskTransitionCommand> edgeCmds = new ArrayList<>();
        for (TaskStepTransitionEntity edge : transitions) {
            edgeCmds.add(new TaskTransitionCommand(
                    codeById.get(edge.getFromStepId()),
                    codeById.get(edge.getToStepId()),
                    edge.getConditionExpr(),
                    edge.getPriority()));
        }
        List<TaskActionCommand> actionCmds = new ArrayList<>();
        for (TaskPlatformActionEntity action : taskActions) {
            actionCmds.add(new TaskActionCommand(
                    "TASK",
                    null,
                    action.getPlatform(),
                    action.getActionType(),
                    TaskJson.map(action.getParams()),
                    action.getButtonText()));
        }
        Map<Long, String> stepCodeById = codeById;
        for (TaskStepPlatformActionEntity action : stepActions) {
            actionCmds.add(new TaskActionCommand(
                    "STEP",
                    stepCodeById.get(action.getStepId()),
                    action.getPlatform(),
                    action.getActionType(),
                    TaskJson.map(action.getParams()),
                    action.getButtonText()));
        }
        int version = entity.getVersion() == null ? 0 : entity.getVersion();
        return new TaskDefinitionAggregateResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getIconUrl(),
                entity.getBadgeText(),
                TaskTime.toInstant(entity.getStartTime()),
                TaskTime.toInstant(entity.getEndTime()),
                entity.getSortWeight() == null ? 0 : entity.getSortWeight(),
                entity.getStatus(),
                version,
                version,
                entity.getPendingRevision() != null && entity.getPendingRevision() == 1,
                entity.getCycleType(),
                entity.getCronExpr(),
                TaskTime.toInstant(entity.getSpecialStart()),
                TaskTime.toInstant(entity.getSpecialEnd()),
                entity.getMutexGroupId(),
                new TaskGrayCommand(
                        entity.getGrayType(),
                        entity.getGrayRatio(),
                        entity.getGrayAbGroup(),
                        entity.getGrayCrowdId(),
                        entity.getGrayExcludeCrowdId()),
                new TaskFilterCommand(
                        entity.getFilterExpr(),
                        TaskJson.longs(entity.getFilterAllowCrowdIds()),
                        TaskJson.longs(entity.getFilterExcludeCrowdIds())),
                List.copyOf(stepCmds),
                List.copyOf(edgeCmds),
                List.copyOf(actionCmds));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
