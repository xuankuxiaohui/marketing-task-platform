package com.mkt.task.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.response.CrowdResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read entries extracted from {@link TaskCrowdAppService} (R22-3).
 * Move-only: get and page semantics unchanged.
 * {@code require} and {@code toResponse} are copied so the moved body stays verbatim.
 */
@Component
public class CrowdReads {

    private final TaskCrowdStore store;

    @Autowired
    public CrowdReads(TaskCrowdStore store) {
        this.store = store;
    }

    CrowdResponse get(long id) {
        return toResponse(require(id));
    }

    PageData<CrowdResponse> page(PageQuery page) {
        long total = store.countAll();
        List<TaskCrowdEntity> rows = store.list(page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(CrowdReads::toResponse).toList());
    }

    private TaskCrowdEntity require(long id) {
        TaskCrowdEntity existing = store.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private static CrowdResponse toResponse(TaskCrowdEntity entity) {
        return new CrowdResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getItemCount() == null ? 0 : entity.getItemCount(),
                entity.getStatus(),
                TaskTime.toInstant(entity.getUpdatedAt()));
    }
}
