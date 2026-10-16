package com.mkt.task.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.command.CrowdImportCommand;
import com.mkt.task.command.CrowdSaveCommand;
import com.mkt.task.response.CrowdImportResponse;
import com.mkt.task.response.CrowdResponse;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCrowdAppService {

    private final PlatformCache cache;
    private final CrowdWrites crowdWrites;
    private final CrowdImport crowdImport;
    private final CrowdReads crowdReads;

    @Autowired
    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            ObjectProvider<UserAttributePort> users,
            TaskSettings settings,
            ObjectProvider<PlatformCache> cache,
            Clock clock,
            CrowdWrites crowdWrites,
            CrowdImport crowdImport,
            CrowdReads crowdReads) {
        this(
                store,
                definitions,
                users.getIfAvailable(),
                settings,
                cache.getIfAvailable(),
                clock,
                crowdWrites,
                crowdImport,
                crowdReads);
    }

    public TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock) {
        this(store, definitions, users, settings, cache, clock, null, null, null);
    }

    private TaskCrowdAppService(
            TaskCrowdStore store,
            TaskDefinitionStore definitions,
            UserAttributePort users,
            TaskSettings settings,
            PlatformCache cache,
            Clock clock,
            CrowdWrites crowdWrites,
            CrowdImport crowdImport,
            CrowdReads crowdReads) {
        this.cache = cache;
        // Manual/IT chain passes null. Production passes the injected collaborators.
        this.crowdWrites = crowdWrites != null
                ? crowdWrites
                : new CrowdWrites(store, definitions, cache, clock);
        this.crowdImport = crowdImport != null
                ? crowdImport
                : new CrowdImport(store, users, settings, cache, clock);
        this.crowdReads = crowdReads != null
                ? crowdReads
                : new CrowdReads(store);
    }

    public CrowdResponse get(long id) {
        return crowdReads.get(id);
    }

    public PageData<CrowdResponse> page(PageQuery page) {
        return crowdReads.page(page);
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

    private void evictCrowd(long crowdId) {
        if (cache != null) {
            cache.evictAfterCommit(CacheNamespace.TASK_CROWD, String.valueOf(crowdId));
        }
    }
}
