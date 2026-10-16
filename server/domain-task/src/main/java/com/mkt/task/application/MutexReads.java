package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.response.MutexGroupResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read entries extracted from {@link TaskMutexGroupAppService} (R26-1).
 * Move-only: get and page semantics unchanged.
 * {@code require} and {@code toResponse} are copied so the moved body stays verbatim.
 * Create, update, and delete still call the copies that remain on the service.
 */
@Component
public class MutexReads {

    private final TaskMutexGroupStore store;

    @Autowired
    public MutexReads(TaskMutexGroupStore store) {
        this.store = store;
    }

    MutexGroupResponse get(long id) {
        return toResponse(require(id));
    }

    PageData<MutexGroupResponse> page(PageQuery page) {
        long total = store.countAll();
        List<TaskMutexGroupEntity> rows = store.list(page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(MutexReads::toResponse).toList());
    }

    private TaskMutexGroupEntity require(long id) {
        TaskMutexGroupEntity existing = store.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
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
