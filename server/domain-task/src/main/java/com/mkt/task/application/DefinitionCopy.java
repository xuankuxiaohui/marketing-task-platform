package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.TaskCopyCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskDefinitionSaveResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Admin definition copy extracted from {@link TaskDefinitionAppService} (R25-2).
 * Move-only: copy semantics unchanged.
 * {@code saveAggregate} stays on the service; {@code @Lazy} breaks the constructor cycle.
 */
@Component
public class DefinitionCopy {

    private final TaskDefinitionRead read;
    private final TaskDefinitionAppService definitions;

    @Autowired
    public DefinitionCopy(TaskDefinitionRead read, @Lazy TaskDefinitionAppService definitions) {
        this.read = read;
        this.definitions = definitions;
    }

    TaskDefinitionSaveResponse copy(long id, TaskCopyCommand command) {
        if (!TaskCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 格式非法");
        }
        TaskDefinitionEntity source = read.requireLive(id);
        TaskDefinitionAggregateResponse view = read.toAggregate(source);
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
        return definitions.saveAggregate(copy);
    }
}
