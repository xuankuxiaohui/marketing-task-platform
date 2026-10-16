package com.mkt.task.application;

import com.mkt.kernel.PageData;
import com.mkt.task.application.TaskDefinitionValidate.ValidatedAggregate;
import com.mkt.task.command.TaskCopyCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.query.TaskDefinitionQuery;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskDefinitionSaveResponse;
import com.mkt.task.response.TaskDefinitionView;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskDefinitionAppService {

    private final Clock clock;
    private final TaskDefinitionValidate validate;
    private final TaskDefinitionPersist persist;
    private final TaskDefinitionRead read;
    private final DefinitionPage definitionPage;
    private final DefinitionCopy definitionCopy;
    private final DefinitionWrites definitionWrites;

    @Autowired
    public TaskDefinitionAppService(
            TaskDefinitionStore definitions,
            Clock clock,
            TaskDefinitionValidate validate,
            TaskDefinitionPersist persist,
            TaskDefinitionRead read,
            DefinitionPage definitionPage,
            DefinitionCopy definitionCopy,
            DefinitionWrites definitionWrites) {
        this.clock = clock;
        this.validate = validate;
        this.persist = persist;
        this.read = read;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.definitionPage =
                definitionPage != null
                        ? definitionPage
                        : new DefinitionPage(definitions);
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.definitionCopy =
                definitionCopy != null
                        ? definitionCopy
                        : new DefinitionCopy(read, this);
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.definitionWrites =
                definitionWrites != null
                        ? definitionWrites
                        : new DefinitionWrites(definitions, clock, validate, persist);
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
                clock,
                new TaskDefinitionValidate(definitions, mutexGroups, crowds, settings),
                new TaskDefinitionPersist(definitions, children, clock),
                new TaskDefinitionRead(definitions, children),
                null,
                null,
                null);
    }

    public TaskDefinitionAggregateResponse get(long id) {
        TaskDefinitionEntity entity = read.requireLive(id);
        return read.toAggregate(entity);
    }

    public PageData<TaskDefinitionView> page(TaskDefinitionQuery query) {
        return definitionPage.page(query);
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
        return definitionCopy.copy(id, command);
    }

    @Transactional
    public void restoreEditState(long id, TaskDefinitionSaveCommand command) {
        definitionWrites.restoreEditState(id, command);
    }

    @Transactional
    public void delete(long id) {
        definitionWrites.delete(id);
    }
}
