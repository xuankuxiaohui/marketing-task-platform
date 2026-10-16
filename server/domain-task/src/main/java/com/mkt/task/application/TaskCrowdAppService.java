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
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.response.CrowdImportResponse;
import com.mkt.task.response.CrowdResponse;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCrowdAppService {

    private final TaskCrowdStore store;
    private final PlatformCache cache;
    private final CrowdWrites crowdWrites;
    private final CrowdImport crowdImport;

    @Autowired
    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            ObjectProvider<UserAttributePort> users,
            TaskSettings settings,
            ObjectProvider<PlatformCache> cache,
            Clock clock,
            CrowdWrites crowdWrites,
            CrowdImport crowdImport) {
        this(
                store,
                definitions,
                users.getIfAvailable(),
                settings,
                cache.getIfAvailable(),
                clock,
                crowdWrites,
                crowdImport);
    }

    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock) {
        this(store, definitions, users, settings, cache, clock, null, null);
    }

    private TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock,
            CrowdWrites crowdWrites,
            CrowdImport crowdImport) {
        this.store = store;
        this.cache = cache;
        // Manual/IT chain passes null. Production passes the injected collaborators.
        this.crowdWrites = crowdWrites != null
                ? crowdWrites
                : new CrowdWrites(store, definitions, cache, clock);
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
        return crowdWrites.create(command);
    }

    @Transactional
    public CrowdResponse update(long id, CrowdSaveCommand command) {
        return crowdWrites.update(id, command);
    }

    @Transactional
    public void delete(long id) {
        crowdWrites.delete(id);
    }

    @Transactional
    public CrowdImportResponse importUsers(long id, CrowdImportCommand command) {
        return crowdImport.importUsers(id, command);
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
