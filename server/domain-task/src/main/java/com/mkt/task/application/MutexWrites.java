package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.MutexGroupSaveCommand;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.response.MutexGroupResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Write entries extracted from {@link TaskMutexGroupAppService} (R26-2).
 * Move-only: create, update, and delete semantics unchanged.
 * {@code require}, {@code requireValid}, and {@code toResponse} moved with the write path
 * so the service no longer keeps those write-only copies.
 * Read-path copies stay on {@link MutexReads}.
 */
@Component
public class MutexWrites {

    private final TaskMutexGroupStore store;
    private final TaskDefinitionStore definitions;
    private final Clock clock;

    @Autowired
    public MutexWrites(TaskMutexGroupStore store, TaskDefinitionStore definitions, Clock clock) {
        this.store = store;
        this.definitions = definitions;
        this.clock = clock;
    }

    MutexGroupResponse create(MutexGroupSaveCommand command) {
        MutexGroupSaveCommand normalized = requireValid(command);
        if (store.getByCode(normalized.code()) != null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "互斥组编码已存在");
        }
        TaskMutexGroupEntity entity = new TaskMutexGroupEntity();
        entity.setCode(normalized.code());
        entity.setName(normalized.name());
        entity.setCrossCycle(Boolean.TRUE.equals(normalized.crossCycle()) ? 1 : 0);
        entity.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        try {
            store.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "互斥组编码已存在", ex);
        }
        return toResponse(entity);
    }

    MutexGroupResponse update(long id, MutexGroupSaveCommand command) {
        TaskMutexGroupEntity existing = require(id);
        MutexGroupSaveCommand normalized = requireValid(command);
        if (!existing.getCode().equals(normalized.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        existing.setName(normalized.name());
        existing.setCrossCycle(Boolean.TRUE.equals(normalized.crossCycle()) ? 1 : 0);
        store.update(existing);
        return toResponse(existing);
    }

    void delete(long id) {
        require(id);
        if (definitions.countByMutexGroup(id) > 0) {
            throw new BusinessException(TaskErrorCodes.MUTEX_IN_USE);
        }
        store.deleteById(id);
    }

    private TaskMutexGroupEntity require(long id) {
        TaskMutexGroupEntity existing = store.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static MutexGroupSaveCommand requireValid(MutexGroupSaveCommand command) {
        if (command == null || !TaskCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 格式非法");
        }
        return new MutexGroupSaveCommand(command.code().trim(), command.name().trim(), command.crossCycle());
    }

    private static MutexGroupResponse toResponse(TaskMutexGroupEntity entity) {
        return new MutexGroupResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.crossCycleFlag(),
                TaskTime.toInstant(entity.getCreatedAt()));
    }
}
