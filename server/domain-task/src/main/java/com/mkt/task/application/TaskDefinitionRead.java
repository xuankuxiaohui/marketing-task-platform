package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskStepEntity;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read helpers extracted from {@link TaskDefinitionAppService} (R5-3).
 * Move-only: toAggregate / requireLive semantics unchanged.
 */
@Component
public class TaskDefinitionRead {

    private final TaskDefinitionStore definitions;
    private final TaskChildStore children;

    @Autowired
    public TaskDefinitionRead(TaskDefinitionStore definitions, TaskChildStore children) {
        this.definitions = definitions;
        this.children = children;
    }

    TaskDefinitionAggregateResponse toAggregate(TaskDefinitionEntity entity) {
        List<TaskStepEntity> steps = children.listSteps(entity.getId());
        List<Long> stepIds = steps.stream().map(TaskStepEntity::getId).toList();
        return TaskDefinitionConvert.toAggregate(
                entity,
                steps,
                children.listTransitions(entity.getId()),
                children.listTaskActions(entity.getId()),
                children.listStepActions(stepIds));
    }

    TaskDefinitionEntity requireLive(long id) {
        TaskDefinitionEntity existing = definitions.getById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }
}
