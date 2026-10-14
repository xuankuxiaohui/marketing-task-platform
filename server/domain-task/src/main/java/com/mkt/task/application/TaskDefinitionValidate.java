package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.domain.ActionSchemas;
import com.mkt.task.domain.CronExprs;
import com.mkt.task.domain.CycleTypes;
import com.mkt.task.domain.GraphEdge;
import com.mkt.task.domain.GraphStep;
import com.mkt.task.domain.GrayTypes;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.domain.TaskGraphValidator;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.expression.ExpressionCompileException;
import com.mkt.task.expression.ExpressionEngine;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validation helpers extracted from {@link TaskDefinitionAppService} (R5-1).
 * Move-only: validate and its private validation cluster semantics unchanged.
 */
@Component
public class TaskDefinitionValidate {

    private final TaskDefinitionStore definitions;
    private final TaskMutexGroupStore mutexGroups;
    private final TaskCrowdStore crowds;
    private final TaskSettings settings;

    @Autowired
    public TaskDefinitionValidate(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskCrowdStore crowds,
            TaskSettings settings) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.crowds = crowds;
        this.settings = settings;
    }

    ValidatedAggregate validate(TaskDefinitionSaveCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (!TaskCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 格式非法");
        }
        String cycleType = command.cycleType() == null ? "" : command.cycleType().trim().toUpperCase(Locale.ROOT);
        if (!CycleTypes.valid(cycleType)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "cycleType 非法");
        }
        if (CycleTypes.CRON.equals(cycleType)) {
            if (command.cronExpr() == null || command.cronExpr().isBlank()) {
                throw new BusinessException(TaskErrorCodes.CYCLE_EMPTY);
            }
            if (!CronExprs.valid(command.cronExpr().trim())) {
                throw new BusinessException(TaskErrorCodes.CYCLE_EMPTY);
            }
        }
        if (CycleTypes.SPECIAL.equals(cycleType)) {
            if (command.specialStart() == null || command.specialEnd() == null) {
                throw new BusinessException(TaskErrorCodes.CYCLE_EMPTY);
            }
            if (!command.specialStart().isBefore(command.specialEnd())) {
                throw new BusinessException(TaskErrorCodes.TIME_WINDOW_INVALID);
            }
        }
        if (command.startTime() != null
                && command.endTime() != null
                && !command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(TaskErrorCodes.TIME_WINDOW_INVALID);
        }
        TaskGrayCommand gray = normalizeGray(command.gray());
        validateFilter(command.filter());
        validateMutex(command.mutexGroupId(), cycleType, command.id());
        List<TaskStepCommand> steps = command.steps() == null ? List.of() : command.steps();
        if (steps.size() > settings.stepMaxCount()) {
            throw new BusinessException(TaskErrorCodes.STEP_COUNT_EXCEEDED);
        }
        List<TaskStepCommand> normalizedSteps = validateSteps(steps);
        List<TaskTransitionCommand> transitions =
                command.transitions() == null ? List.of() : command.transitions();
        validateGraph(normalizedSteps, transitions);
        List<TaskActionCommand> actions = normalizeActions(
                command.actions() == null ? List.of() : command.actions(), normalizedSteps);
        return new ValidatedAggregate(
                new TaskDefinitionSaveCommand(
                        command.id(),
                        command.code().trim(),
                        command.name().trim(),
                        command.description(),
                        command.category(),
                        command.iconUrl(),
                        command.badgeText(),
                        command.startTime(),
                        command.endTime(),
                        command.sortWeight(),
                        cycleType,
                        command.cronExpr(),
                        command.specialStart(),
                        command.specialEnd(),
                        command.mutexGroupId(),
                        gray,
                        command.filter(),
                        normalizedSteps,
                        transitions,
                        actions),
                normalizedSteps,
                transitions,
                actions);
    }

    private TaskGrayCommand normalizeGray(TaskGrayCommand gray) {
        if (gray == null) {
            return null;
        }
        String type = gray.type() == null ? GrayTypes.NONE : gray.type().trim().toUpperCase(Locale.ROOT);
        if (!GrayTypes.valid(type)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "gray.type 非法");
        }
        if (GrayTypes.RATIO.equals(type)) {
            if (gray.ratio() == null || gray.ratio() < 0 || gray.ratio() > 100) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "gray.ratio 须为 0-100");
            }
        }
        String group = gray.abGroup() == null ? null : gray.abGroup().trim().toUpperCase(Locale.ROOT);
        if (GrayTypes.AB.equals(type)) {
            if (group == null || !Set.of("A", "B", "AB").contains(group)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "gray.abGroup 非法");
            }
        }
        if (GrayTypes.CROWD.equals(type)) {
            requireCrowd(gray.crowdId());
            if (gray.excludeCrowdId() != null) {
                requireCrowd(gray.excludeCrowdId());
            }
        }
        return new TaskGrayCommand(type, gray.ratio(), group, gray.crowdId(), gray.excludeCrowdId());
    }

    private void validateFilter(TaskFilterCommand filter) {
        if (filter == null) {
            return;
        }
        if (filter.expr() != null && !filter.expr().isBlank()) {
            try {
                ExpressionEngine.requireValid(filter.expr());
            } catch (ExpressionCompileException ex) {
                throw new BusinessException(TaskErrorCodes.EXPRESSION_INVALID, ex.detail().reason(), ex);
            }
        }
        requireCrowds(filter.allowCrowdIds());
        requireCrowds(filter.excludeCrowdIds());
    }

    private void validateMutex(Long mutexGroupId, String cycleType, Long taskId) {
        if (mutexGroupId == null) {
            return;
        }
        TaskMutexGroupEntity group = mutexGroups.getById(mutexGroupId);
        if (group == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "互斥组不存在");
        }
        List<String> types = definitions.cycleTypesInMutexGroup(mutexGroupId, taskId);
        for (String existing : types) {
            if (!cycleType.equals(existing)) {
                throw new BusinessException(TaskErrorCodes.MUTEX_CYCLE_MISMATCH);
            }
        }
    }

    private List<TaskStepCommand> validateSteps(List<TaskStepCommand> steps) {
        Set<String> codes = new HashSet<>();
        Set<Integer> seqs = new HashSet<>();
        List<TaskStepCommand> normalized = new ArrayList<>();
        for (TaskStepCommand step : steps) {
            if (step == null || step.code() == null || step.code().isBlank() || step.seq() == null || step.seq() < 1) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "步骤编码或顺序号非法");
            }
            String code = step.code().trim();
            String type = step.type() == null ? "" : step.type().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) {
                throw new BusinessException(TaskErrorCodes.STEP_CODE_DUPLICATE);
            }
            if (!seqs.add(step.seq())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "步骤顺序号重复");
            }
            if (!StepTypes.valid(type)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "步骤类型非法");
            }
            if (StepTypes.PROGRESS.equals(type)) {
                if (step.progressTarget() == null || step.progressTarget() < 1) {
                    throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "进度目标非法");
                }
            }
            if (StepTypes.REWARD.equals(type) && step.prizeId() == null) {
                throw new BusinessException(TaskErrorCodes.REWARD_PRIZE_INVALID);
            }
            normalized.add(new TaskStepCommand(
                    code, step.name().trim(), step.seq(), type, step.progressTarget(), step.prizeId()));
        }
        return List.copyOf(normalized);
    }

    private void validateGraph(List<TaskStepCommand> steps, List<TaskTransitionCommand> transitions) {
        List<GraphStep> graphSteps = steps.stream().map(s -> new GraphStep(s.code(), s.seq())).toList();
        List<GraphEdge> graphEdges = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (TaskStepCommand step : steps) {
            codes.add(step.code());
        }
        for (TaskTransitionCommand edge : transitions) {
            if (edge == null || !codes.contains(edge.fromStepCode()) || !codes.contains(edge.toStepCode())) {
                throw new BusinessException(TaskErrorCodes.DAG_CYCLE);
            }
            if (edge.conditionExpr() != null && !edge.conditionExpr().isBlank()) {
                try {
                    ExpressionEngine.requireValid(edge.conditionExpr());
                } catch (ExpressionCompileException ex) {
                    throw new BusinessException(TaskErrorCodes.EXPRESSION_INVALID, ex.detail().reason(), ex);
                }
            }
            graphEdges.add(new GraphEdge(edge.fromStepCode(), edge.toStepCode()));
        }
        TaskGraphValidator.SaveResult result = TaskGraphValidator.validateSave(graphSteps, graphEdges);
        if (result == TaskGraphValidator.SaveResult.DUPLICATE_CODE) {
            throw new BusinessException(TaskErrorCodes.STEP_CODE_DUPLICATE);
        }
        if (result != TaskGraphValidator.SaveResult.OK) {
            throw new BusinessException(TaskErrorCodes.DAG_CYCLE);
        }
    }

    private List<TaskActionCommand> normalizeActions(List<TaskActionCommand> actions, List<TaskStepCommand> steps) {
        Set<String> stepCodes = new HashSet<>();
        for (TaskStepCommand step : steps) {
            stepCodes.add(step.code());
        }
        Set<String> taskPlatforms = new HashSet<>();
        Set<String> stepPlatforms = new HashSet<>();
        List<TaskActionCommand> normalized = new ArrayList<>();
        for (TaskActionCommand action : actions) {
            if (action == null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "动作配置非法");
            }
            String scope = action.scope() == null ? "" : action.scope().trim().toUpperCase(Locale.ROOT);
            String platform = action.platform() == null ? "" : action.platform().trim().toUpperCase(Locale.ROOT);
            String type = action.actionType() == null ? "" : action.actionType().trim().toUpperCase(Locale.ROOT);
            String stepCode = action.stepCode() == null ? null : action.stepCode().trim();
            if (!ActionSchemas.validPlatform(platform) || !ActionSchemas.validType(type)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "动作配置非法");
            }
            if (!ActionSchemas.validParams(type, action.params(), action.buttonText())) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "动作参数非法");
            }
            if ("TASK".equals(scope)) {
                if (!taskPlatforms.add(platform)) {
                    throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "任务级动作端重复");
                }
            } else if ("STEP".equals(scope)) {
                if (stepCode == null || !stepCodes.contains(stepCode)) {
                    throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "步骤级动作未引用步骤");
                }
                if (!stepPlatforms.add(stepCode + ":" + platform)) {
                    throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "步骤级动作端重复");
                }
            } else {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "动作 scope 非法");
            }
            normalized.add(new TaskActionCommand(scope, stepCode, platform, type, action.params(), action.buttonText()));
        }
        return List.copyOf(normalized);
    }

    private void requireCrowds(List<Long> ids) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            requireCrowd(id);
        }
    }

    private void requireCrowd(Long id) {
        if (id == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包不存在");
        }
        if (crowds.getById(id) == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包不存在");
        }
    }

    record ValidatedAggregate(
            TaskDefinitionSaveCommand command,
            List<TaskStepCommand> steps,
            List<TaskTransitionCommand> transitions,
            List<TaskActionCommand> actions) {}
}
