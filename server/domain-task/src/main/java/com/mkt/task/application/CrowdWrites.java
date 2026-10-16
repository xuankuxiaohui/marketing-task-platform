package com.mkt.task.application;

import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.command.CrowdSaveCommand;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CrowdStatuses;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.response.CrowdResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Write entries extracted from {@link TaskCrowdAppService} (R22-2).
 * Move-only: create, update, delete, and requireValid semantics unchanged.
 * {@code require}, {@code evictCrowd}, and {@code toResponse} are copied so the moved body stays verbatim.
 */
@Component
public class CrowdWrites {

    private final TaskCrowdStore store;
    private final TaskDefinitionStore definitions;
    private final PlatformCache cache;
    private final Clock clock;

    @Autowired
    public CrowdWrites(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            ObjectProvider<PlatformCache> cache,
            Clock clock) {
        this(store, definitions, cache.getIfAvailable(), clock);
    }

    CrowdWrites(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            PlatformCache cache,
            Clock clock) {
        this.store = store;
        this.definitions = definitions;
        this.cache = cache;
        this.clock = clock;
    }

    CrowdResponse create(CrowdSaveCommand command) {
        CrowdSaveCommand normalized = requireValid(command);
        if (store.getByCode(normalized.code()) != null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包编码已存在");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        TaskCrowdEntity entity = new TaskCrowdEntity();
        entity.setCode(normalized.code());
        entity.setName(normalized.name());
        entity.setStatus(normalized.status());
        entity.setItemCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            store.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包编码已存在", ex);
        }
        return toResponse(entity);
    }

    CrowdResponse update(long id, CrowdSaveCommand command) {
        TaskCrowdEntity existing = require(id);
        CrowdSaveCommand normalized = requireValid(command);
        if (!existing.getCode().equals(normalized.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 不可修改");
        }
        existing.setName(normalized.name());
        existing.setStatus(normalized.status());
        existing.setUpdatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        store.update(existing);
        evictCrowd(id);
        return toResponse(existing);
    }

    void delete(long id) {
        require(id);
        if (definitions.countReferencingCrowd(id) > 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包仍被任务引用");
        }
        store.deleteById(id);
        evictCrowd(id);
    }

    private CrowdSaveCommand requireValid(CrowdSaveCommand command) {
        if (command == null || !TaskCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "code 格式非法");
        }
        String status = command.status() == null || command.status().isBlank()
                ? CrowdStatuses.ENABLED
                : command.status().trim().toUpperCase(Locale.ROOT);
        if (!CrowdStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "status 仅允许 ENABLED|DISABLED");
        }
        return new CrowdSaveCommand(command.code().trim(), command.name().trim(), status);
    }

    private TaskCrowdEntity require(long id) {
        TaskCrowdEntity existing = store.getById(id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private void evictCrowd(long crowdId) {
        if (cache != null) {
            cache.evictAfterCommit(CacheNamespace.TASK_CROWD, String.valueOf(crowdId));
        }
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
