package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.command.MutexGroupSaveCommand;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.response.MutexGroupResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskMutexGroupAppService {

    private final TaskMutexGroupStore store;
    private final TaskDefinitionStore definitions;
    private final Clock clock;

    public TaskMutexGroupAppService(TaskMutexGroupStore store, TaskDefinitionStore definitions, Clock clock) {
        this.store = store;
        this.definitions = definitions;
        this.clock = clock;
    }

    public MutexGroupResponse get(long id) {
        return toResponse(require(id));
    }

    public PageData<MutexGroupResponse> page(PageQuery page) {
        long total = store.countAll();
        List<TaskMutexGroupEntity> rows = store.list(page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(TaskMutexGroupAppService::toResponse).toList());
    }

    @Transactional
    public MutexGroupResponse create(MutexGroupSaveCommand command) {
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

    @Transactional
    public MutexGroupResponse update(long id, MutexGroupSaveCommand command) {
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

    @Transactional
    public void delete(long id) {
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
