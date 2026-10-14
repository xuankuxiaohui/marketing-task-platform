package com.mkt.task.application;

import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.support.TaskOperator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Publish freeze / snapshot / cache helpers extracted from {@link TaskPublishAppService} (R1-5).
 * Move-only: freezeToPublished / insertSnapshot / cacheSnapshot / evictPublishedIndex unchanged.
 */
@Component
public class TaskPublishFreeze {

    private static final Logger log = LoggerFactory.getLogger(TaskPublishFreeze.class);

    private final TaskDefinitionStore definitions;
    private final TaskMutexGroupStore mutexGroups;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskDefinitionAppService definitionsApp;
    private final Clock clock;
    private final ObjectProvider<PlatformCache> cache;
    private final PlatformCache cacheDirect;

    @Autowired
    public TaskPublishFreeze(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            Clock clock,
            ObjectProvider<PlatformCache> cache) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
        this.clock = clock;
        this.cache = cache;
        this.cacheDirect = null;
    }

    TaskPublishFreeze(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            Clock clock,
            PlatformCache cache) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
        this.clock = clock;
        this.cache = null;
        this.cacheDirect = cache;
    }

    boolean freezeToPublished(TaskDefinitionEntity entity, boolean stayPublished) {
        LocalDateTime now = nowUtc();
        String expectedStatus = entity.getStatus();
        int expectedVersion = versionOf(entity);
        int expectedPending = pending(entity) ? 1 : 0;
        int nextVersion = expectedVersion + 1;
        insertSnapshot(entity, nextVersion, now);
        int rows = definitions.casPublish(
                entity.getId(), expectedStatus, expectedVersion, expectedPending, nextVersion, now);
        if (rows == 0) {
            return false;
        }
        entity.setVersion(nextVersion);
        entity.setStatus(DefinitionStatuses.PUBLISHED);
        entity.setPendingRevision(0);
        entity.setSchedulePublishAt(null);
        entity.setOfflineAt(null);
        entity.setUpdatedAt(now);
        evictPublishedIndex();
        cacheSnapshot(entity.getId(), nextVersion);
        if (stayPublished) {
            log.info("published revision, taskId={}, version={}", entity.getId(), nextVersion);
        } else {
            log.info("published task, taskId={}, version={}", entity.getId(), nextVersion);
        }
        return true;
    }

    private void insertSnapshot(TaskDefinitionEntity entity, int version, LocalDateTime now) {
        if (snapshots.getByTaskAndVersion(entity.getId(), version) != null) {
            return;
        }
        TaskDefinitionAggregateResponse view = definitionsApp.get(entity.getId());
        String mutexCode = null;
        if (entity.getMutexGroupId() != null) {
            TaskMutexGroupEntity group = mutexGroups.getById(entity.getMutexGroupId());
            mutexCode = group == null ? null : group.getCode();
        }
        SnapshotContent content = TaskDefinitionConvert.toSnapshot(view, mutexCode);
        TaskVersionSnapshotEntity row = new TaskVersionSnapshotEntity();
        row.setTaskId(entity.getId());
        row.setVersion(version);
        row.setContent(JsonUtil.toJson(content));
        row.setPublishedAt(now);
        long publisher;
        try {
            publisher = TaskOperator.requireUserId();
        } catch (BusinessException ex) {
            publisher = 0L;
        }
        row.setPublishedBy(publisher);
        try {
            snapshots.insert(row);
        } catch (DuplicateKeyException ignored) {
            // peer already froze this version
        }
    }

    void evictPublishedIndex() {
        PlatformCache platformCache = cache();
        if (platformCache == null) {
            return;
        }
        platformCache.evictAfterCommit(CacheNamespace.TASK_PUBLISHED_INDEX, "all");
    }

    private void cacheSnapshot(long taskId, int version) {
        PlatformCache platformCache = cache();
        if (platformCache == null) {
            return;
        }
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(taskId, version);
        if (snap != null) {
            platformCache.putAfterCommit(CacheNamespace.TASK_SNAPSHOT, taskId + ":" + version, snap.getContent());
        }
    }

    private PlatformCache cache() {
        return cacheDirect != null ? cacheDirect : (cache == null ? null : cache.getIfAvailable());
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static boolean pending(TaskDefinitionEntity entity) {
        return entity.getPendingRevision() != null && entity.getPendingRevision() == 1;
    }

    private static int versionOf(TaskDefinitionEntity entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }
}
