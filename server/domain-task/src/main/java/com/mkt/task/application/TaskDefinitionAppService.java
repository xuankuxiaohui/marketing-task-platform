package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.task.application.TaskDefinitionValidate.ValidatedAggregate;
import com.mkt.task.command.TaskCopyCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskStepEntity;
import com.mkt.task.query.TaskDefinitionQuery;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskDefinitionSaveResponse;
import com.mkt.task.response.TaskDefinitionView;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskDefinitionAppService {

    private final TaskDefinitionStore definitions;
    private final TaskChildStore children;
    private final Clock clock;
    private final TaskDefinitionValidate validate;
    private final TaskDefinitionPersist persist;

    @Autowired
    public TaskDefinitionAppService(
            TaskDefinitionStore definitions,
            TaskChildStore children,
            Clock clock,
            TaskDefinitionValidate validate,
            TaskDefinitionPersist persist) {
        this.definitions = definitions;
        this.children = children;
        this.clock = clock;
        this.validate = validate;
        this.persist = persist;
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
                new TaskDefinitionValidate(definitions, mutexGroups, crowds, settings),
                new TaskDefinitionPersist(definitions, children, clock));
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
            return persist.insertNew(validated, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        }
        return persist.updateExisting(validated);
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
        persist.persistChildren(existing.getId(), validated);
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
