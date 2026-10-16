package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.application.TaskDefinitionValidate.ValidatedAggregate;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Admin definition restore and delete extracted from {@link TaskDefinitionAppService} (R25-3).
 * Move-only: restoreEditState / delete semantics unchanged.
 */
@Component
public class DefinitionWrites {

    private final TaskDefinitionStore definitions;
    private final Clock clock;
    private final TaskDefinitionValidate validate;
    private final TaskDefinitionPersist persist;

    @Autowired
    public DefinitionWrites(
            TaskDefinitionStore definitions,
            Clock clock,
            TaskDefinitionValidate validate,
            TaskDefinitionPersist persist) {
        this.definitions = definitions;
        this.clock = clock;
        this.validate = validate;
        this.persist = persist;
    }

    void restoreEditState(long id, TaskDefinitionSaveCommand command) {
        ValidatedAggregate validated = validate.validate(command);
        TaskDefinitionEntity existing = requireLockedLive(id);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        TaskDefinitionConvert.applyHeader(validated.command(), existing);
        existing.setPendingRevision(0);
        existing.setUpdatedAt(now);
        definitions.update(existing);
        persist.persistChildren(existing.getId(), validated);
    }

    void delete(long id) {
        TaskDefinitionEntity existing = requireLockedLive(id);
        if (!DefinitionStatuses.deletable(existing.getStatus())) {
            throw new BusinessException(TaskErrorCodes.PUBLISHED_NOT_DELETABLE);
        }
        existing.setDeleted(1);
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        definitions.update(existing);
    }

    private TaskDefinitionEntity requireLockedLive(long id) {
        TaskDefinitionEntity existing = definitions.getByIdForUpdate(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }
}
