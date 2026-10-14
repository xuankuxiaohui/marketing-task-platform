package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.domain.GraphEdge;
import com.mkt.task.domain.GraphStep;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.domain.TaskGraphValidator;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.expression.ExpressionCompileException;
import com.mkt.task.expression.ExpressionEngine;
import com.mkt.task.response.PublishCheckError;
import com.mkt.task.response.PublishCheckResponse;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Publish validation helpers extracted from {@link TaskPublishAppService} (R1-3).
 * Move-only: checkPublish / rejectIfInvalid / validateFailed / checkErrorsOf semantics unchanged.
 */
@Component
public class TaskPublishChecks {

    private final TaskDefinitionStore definitions;
    private final TaskDefinitionAppService definitionsApp;
    private final PrizeEnabledLookup prizes;

    public TaskPublishChecks(
            TaskDefinitionStore definitions,
            TaskDefinitionAppService definitionsApp,
            PrizeEnabledLookup prizes) {
        this.definitions = definitions;
        this.definitionsApp = definitionsApp;
        this.prizes = prizes;
    }

    void rejectIfInvalid(TaskDefinitionEntity entity) {
        List<PublishCheckError> errors = checkPublish(entity);
        if (!errors.isEmpty()) {
            throw validateFailed(errors);
        }
    }

    List<PublishCheckError> checkPublish(TaskDefinitionEntity entity) {
        List<PublishCheckError> errors = new ArrayList<>();
        TaskDefinitionAggregateResponse view = definitionsApp.get(entity.getId());
        List<TaskStepCommand> steps = view.steps() == null ? List.of() : view.steps();
        List<TaskTransitionCommand> edges = view.transitions() == null ? List.of() : view.transitions();
        if (steps.isEmpty()) {
            errors.add(new PublishCheckError("steps", "至少需要一个步骤"));
        } else {
            List<GraphStep> graphSteps = steps.stream().map(s -> new GraphStep(s.code(), s.seq())).toList();
            List<GraphEdge> graphEdges = edges.stream()
                    .map(e -> new GraphEdge(e.fromStepCode(), e.toStepCode()))
                    .toList();
            if (!TaskGraphValidator.reachableAndOpen(graphSteps, graphEdges)) {
                errors.add(new PublishCheckError("reachability", "存在不可达或死锁步骤"));
            }
        }
        for (TaskStepCommand step : steps) {
            if (StepTypes.REWARD.equals(step.type())) {
                if (step.prizeId() == null || !prizes.enabled(step.prizeId())) {
                    errors.add(new PublishCheckError("reward:" + step.code(), "未引用启用奖品"));
                }
            }
        }
        if (view.filter() != null && view.filter().expr() != null && !view.filter().expr().isBlank()) {
            try {
                ExpressionEngine.requireValid(view.filter().expr());
            } catch (ExpressionCompileException ex) {
                errors.add(new PublishCheckError("expression", ex.detail().reason()));
            }
        }
        for (TaskTransitionCommand edge : edges) {
            if (edge.conditionExpr() == null || edge.conditionExpr().isBlank()) {
                continue;
            }
            try {
                ExpressionEngine.requireValid(edge.conditionExpr());
            } catch (ExpressionCompileException ex) {
                errors.add(new PublishCheckError("expression", ex.detail().reason()));
            }
        }
        if (entity.getStartTime() != null
                && entity.getEndTime() != null
                && !entity.getStartTime().isBefore(entity.getEndTime())) {
            errors.add(new PublishCheckError("time-window", "开始必须早于结束"));
        }
        if (entity.getSchedulePublishAt() != null
                && entity.getEndTime() != null
                && !entity.getSchedulePublishAt().isBefore(entity.getEndTime())) {
            errors.add(new PublishCheckError("schedule", "定时发布时间必须早于时间窗结束"));
        }
        if (entity.getMutexGroupId() != null) {
            List<String> types = definitions.cycleTypesInMutexGroup(entity.getMutexGroupId(), null);
            long distinct = types.stream().distinct().count();
            if (distinct > 1) {
                errors.add(new PublishCheckError("mutex", "互斥组内周期类型不一致"));
            }
        }
        return errors;
    }

    BusinessException validateFailed(List<PublishCheckError> errors) {
        return new BusinessException(
                TaskErrorCodes.PUBLISH_VALIDATE_FAILED,
                TaskErrorCodes.PUBLISH_VALIDATE_FAILED.message(),
                new PublishCheckResponse(List.copyOf(errors)));
    }

    static List<PublishCheckError> checkErrorsOf(Object data) {
        if (data instanceof PublishCheckResponse check && check.checkErrors() != null) {
            return List.copyOf(check.checkErrors());
        }
        return List.of();
    }
}
