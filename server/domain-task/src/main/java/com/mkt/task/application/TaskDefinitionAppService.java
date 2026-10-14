package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.task.application.TaskDefinitionValidate.ValidatedAggregate;
import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskCopyCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.convert.TaskJson;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskPlatformActionEntity;
import com.mkt.task.entity.TaskStepEntity;
import com.mkt.task.entity.TaskStepPlatformActionEntity;
import com.mkt.task.entity.TaskStepTransitionEntity;
import com.mkt.task.query.TaskDefinitionQuery;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskDefinitionSaveResponse;
import com.mkt.task.response.TaskDefinitionView;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskOperator;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskDefinitionAppService {

    private final TaskDefinitionStore definitions;
    private final TaskChildStore children;
    private final Clock clock;
    private final TaskDefinitionValidate validate;

    @Autowired
    public TaskDefinitionAppService(
            TaskDefinitionStore definitions,
            TaskChildStore children,
            Clock clock,
            TaskDefinitionValidate validate) {
        this.definitions = definitions;
        this.children = children;
        this.clock = clock;
        this.validate = validate;
    }

    public TaskDefinitionAppService(
            TaskDefinitionStore definitions,
            TaskChildStore children,
            TaskMutexGroupStore mutexGroups,
            TaskCrowdStore crowds,
            TaskSettings settings,
            Clock clock) {
        this(
                definitions,
                children,
                clock,
                new TaskDefinitionValidate(definitions, mutexGroups, crowds, settings));
    }

    public TaskDefinitionAggregateResponse get(long id) {
        TaskDefinitionEntity entity = requireLive(id);
        return toAggregate(entity);
    }

    public PageData<TaskDefinitionView> page(TaskDefinitionQuery query) {
        String code = blankToNull(query.code());
        String name = blankToNull(query.name());
        String status = blankToNull(query.status());
        String category = blankToNull(query.category());
        if (status != null && !DefinitionStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "status 非法");
        }
        long total = definitions.countByQuery(code, name, status, category);
        List<TaskDefinitionEntity> rows = definitions.listByQuery(
                code, name, status, category, query.page().offset(), query.page().pageSize());
        return new PageData<>(total, rows.stream().map(TaskDefinitionConvert::toView).toList());
    }

    @Transactional
    public TaskDefinitionSaveResponse saveAggregate(TaskDefinitionSaveCommand command) {
        ValidatedAggregate validated = validate.validate(command);
        if (command.id() == null) {
            return insertNew(validated, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        }
        return updateExisting(validated);
    }

    @Transactional
    public TaskDefinitionSaveResponse copy(long id, TaskCopyCommand command) {
        if (!TaskCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 格式非法");
        }
        TaskDefinitionEntity source = requireLive(id);
        TaskDefinitionAggregateResponse view = toAggregate(source);
        TaskDefinitionSaveCommand copy = new TaskDefinitionSaveCommand(
                null,
                command.code().trim(),
                command.name().trim(),
                view.description(),
                view.category(),
                view.iconUrl(),
                view.badgeText(),
                view.startTime(),
                view.endTime(),
                view.sortWeight(),
                view.cycleType(),
                view.cronExpr(),
                view.specialStart(),
                view.specialEnd(),
                view.mutexGroupId(),
                view.gray(),
                view.filter(),
                view.steps(),
                view.transitions(),
                view.actions());
        return saveAggregate(copy);
    }

    @Transactional
    public void restoreEditState(long id, TaskDefinitionSaveCommand command) {
        ValidatedAggregate validated = validate.validate(command);
        TaskDefinitionEntity existing = requireLockedLive(id);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        TaskDefinitionConvert.applyHeader(validated.command(), existing);
        existing.setPendingRevision(0);
        existing.setUpdatedAt(now);
        definitions.update(existing);
        persistChildren(existing.getId(), validated);
    }

    @Transactional
    public void delete(long id) {
        TaskDefinitionEntity existing = requireLockedLive(id);
        if (!DefinitionStatuses.deletable(existing.getStatus())) {
            throw new BusinessException(TaskErrorCodes.PUBLISHED_NOT_DELETABLE);
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        definitions.update(existing);
    }

    private TaskDefinitionSaveResponse insertNew(ValidatedAggregate validated, LocalDateTime now) {
        if (definitions.getByCode(validated.command().code()) != null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "任务编码已存在");
        }
        TaskDefinitionEntity entity = new TaskDefinitionEntity();
        TaskDefinitionConvert.applyHeader(validated.command(), entity);
        entity.setStatus(DefinitionStatuses.DRAFT);
        entity.setVersion(0);
        entity.setPendingRevision(0);
        entity.setDeleted(0);
        entity.setCreatedBy(TaskOperator.requireUserId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            definitions.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "任务编码已存在", ex);
        }
        persistChildren(entity.getId(), validated);
        return new TaskDefinitionSaveResponse(entity.getId(), entity.getCode(), 0, entity.getStatus());
    }

    private TaskDefinitionSaveResponse updateExisting(ValidatedAggregate validated) {
        TaskDefinitionEntity existing = requireLockedLive(validated.command().id());
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!existing.getCode().equals(validated.command().code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        TaskDefinitionConvert.applyHeader(validated.command(), existing);
        if (DefinitionStatuses.OFFLINE.equals(existing.getStatus())) {
            existing.setStatus(DefinitionStatuses.DRAFT);
            existing.setPendingRevision(0);
        } else if (DefinitionStatuses.publishedFamily(existing.getStatus())) {
            existing.setPendingRevision(1);
        }
        existing.setUpdatedAt(now);
        definitions.update(existing);
        persistChildren(existing.getId(), validated);
        int version = existing.getVersion() == null ? 0 : existing.getVersion();
        return new TaskDefinitionSaveResponse(existing.getId(), existing.getCode(), version, existing.getStatus());
    }

    private void persistChildren(long taskId, ValidatedAggregate validated) {
        Map<String, TaskStepEntity> stepsByCode = new HashMap<>();
        List<TaskStepEntity> stepRows = new ArrayList<>();
        for (TaskStepCommand step : validated.steps()) {
            TaskStepEntity row = new TaskStepEntity();
            row.setTaskId(taskId);
            row.setSeq(step.seq());
            row.setCode(step.code());
            row.setName(step.name());
            row.setType(step.type());
            row.setProgressTarget(step.progressTarget());
            row.setPrizeId(step.prizeId());
            stepRows.add(row);
            stepsByCode.put(step.code(), row);
        }
        List<TaskStepTransitionEntity> edgeRows = new ArrayList<>();
        for (TaskTransitionCommand edge : validated.transitions()) {
            TaskStepTransitionEntity row = new TaskStepTransitionEntity();
            row.setTaskId(taskId);
            row.setFromStepId(null);
            row.setToStepId(null);
            row.setConditionExpr(blankToNull(edge.conditionExpr()));
            row.setPriority(edge.priority() == null ? 0 : edge.priority());
            edgeRows.add(row);
        }
        List<TaskPlatformActionEntity> taskActions = new ArrayList<>();
        List<TaskStepPlatformActionEntity> stepActions = new ArrayList<>();
        for (TaskActionCommand action : validated.actions()) {
            if ("TASK".equals(action.scope())) {
                TaskPlatformActionEntity row = new TaskPlatformActionEntity();
                row.setTaskId(taskId);
                row.setPlatform(action.platform());
                row.setActionType(action.actionType());
                row.setParams(TaskJson.map(action.params()));
                row.setButtonText(action.buttonText());
                taskActions.add(row);
            }
        }
        children.replaceChildren(taskId, stepRows, List.of(), taskActions, List.of());
        for (TaskStepEntity step : children.listSteps(taskId)) {
            stepsByCode.put(step.getCode(), step);
        }
        for (int i = 0; i < validated.transitions().size(); i++) {
            TaskTransitionCommand edge = validated.transitions().get(i);
            TaskStepTransitionEntity row = new TaskStepTransitionEntity();
            row.setTaskId(taskId);
            row.setFromStepId(stepsByCode.get(edge.fromStepCode()).getId());
            row.setToStepId(stepsByCode.get(edge.toStepCode()).getId());
            row.setConditionExpr(blankToNull(edge.conditionExpr()));
            row.setPriority(edge.priority() == null ? 0 : edge.priority());
            edgeRows.set(i, row);
        }
        for (TaskActionCommand action : validated.actions()) {
            if ("STEP".equals(action.scope())) {
                TaskStepPlatformActionEntity row = new TaskStepPlatformActionEntity();
                row.setStepId(stepsByCode.get(action.stepCode()).getId());
                row.setPlatform(action.platform());
                row.setActionType(action.actionType());
                row.setParams(TaskJson.map(action.params()));
                row.setButtonText(action.buttonText());
                stepActions.add(row);
            }
        }
        for (TaskStepTransitionEntity edge : edgeRows) {
            children.insertTransition(edge);
        }
        for (TaskStepPlatformActionEntity action : stepActions) {
            children.insertStepAction(action);
        }
    }

    private TaskDefinitionEntity requireLive(long id) {
        TaskDefinitionEntity existing = definitions.getById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private TaskDefinitionEntity requireLockedLive(long id) {
        TaskDefinitionEntity existing = definitions.getByIdForUpdate(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private TaskDefinitionAggregateResponse toAggregate(TaskDefinitionEntity entity) {
        List<TaskStepEntity> steps = children.listSteps(entity.getId());
        List<Long> stepIds = steps.stream().map(TaskStepEntity::getId).toList();
        return TaskDefinitionConvert.toAggregate(
                entity,
                steps,
                children.listTransitions(entity.getId()),
                children.listTaskActions(entity.getId()),
                children.listStepActions(stepIds));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
