package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.command.CrowdImportCommand;
import com.mkt.task.command.CrowdSaveCommand;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CrowdStatuses;
import com.mkt.task.domain.TaskCodes;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.response.CrowdImportResponse;
import com.mkt.task.response.CrowdResponse;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCrowdAppService {

    private final TaskCrowdStore store;
    private final TaskDefinitionStore definitions;
    private final PlatformCache cache;
    private final Clock clock;
    private final CrowdImport crowdImport;

    @Autowired
    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            ObjectProvider<UserAttributePort> users,
            TaskSettings settings,
            ObjectProvider<PlatformCache> cache,
            Clock clock,
            CrowdImport crowdImport) {
        this(store, definitions, users.getIfAvailable(), settings, cache.getIfAvailable(), clock, crowdImport);
    }

    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock) {
        this(store, definitions, users, settings, cache, clock, null);
    }

    private TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock,
            CrowdImport crowdImport) {
        this.store = store;
        this.definitions = definitions;
        this.cache = cache;
        this.clock = clock;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.crowdImport = crowdImport != null
                ? crowdImport
                : new CrowdImport(store, users, settings, cache, clock);
    }

    public CrowdResponse get(long id) {
        return toResponse(require(id));
    }

    public PageData<CrowdResponse> page(PageQuery page) {
        long total = store.countAll();
        List<TaskCrowdEntity> rows = store.list(page.offset(), page.pageSize());
        return new PageData<>(total, rows.stream().map(TaskCrowdAppService::toResponse).toList());
    }

    @Transactional
    public CrowdResponse create(CrowdSaveCommand command) {
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

    @Transactional
    public CrowdResponse update(long id, CrowdSaveCommand command) {
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

    @Transactional
    public void delete(long id) {
        require(id);
        if (definitions.countReferencingCrowd(id) > 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "人群包仍被任务引用");
        }
        store.deleteById(id);
        evictCrowd(id);
    }

    @Transactional
    public CrowdImportResponse importUsers(long id, CrowdImportCommand command) {
        return crowdImport.importUsers(id, command);
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
