package com.mkt.task.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.CycleTypes;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.CachedPublishedCatalog;
import com.mkt.task.response.RewardPreviewView;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Published-catalog + current-instances cluster extracted from {@link TaskPortalAppService} (R1).
 * Move-only: TTL / singleflight / dual-IN / V9 semantics unchanged.
 */
@Component
public class TaskPortalPublishedCatalog {

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final PlatformCache cache;

    /** Process-local reuse of parsed snapshots while L1 returns the same catalog instance. */
    private volatile ParsedPublishedCatalog parsedPublishedCatalog;

    /**
     * Architect 2026-10-12: Caffeine TTL 5s (2–5s band) + {@code get(key, loader)} singleflight for
     * portal list current-instances. Loader uses dual-IN (not oneshot-tuple-only).
     * Keep V9 (idx_user_cycle dropped). 1536M not raised.
     */
    private static final Duration CURRENT_INSTANCES_TTL = Duration.ofSeconds(5);

    private final Cache<Long, Map<Long, TaskInstanceEntity>> currentInstancesByUser =
            Caffeine.newBuilder()
                    .expireAfterWrite(CURRENT_INSTANCES_TTL)
                    .maximumSize(8_192)
                    .build();

    @Autowired
    public TaskPortalPublishedCatalog(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            ObjectProvider<PlatformCache> cache) {
        this(definitions, snapshots, instances, cache.getIfAvailable());
    }

    public TaskPortalPublishedCatalog(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            PlatformCache cache) {
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.cache = cache;
    }

    PublishedBundle publishedBundle() {
        if (cache == null) {
            List<TaskDefinitionEntity> published = definitions.listPublished();
            Map<Long, SnapshotContent> snaps = loadPublishedSnapshotsFromStore(published);
            return new PublishedBundle(published, snaps, rewardPreviewsOf(snaps));
        }
        CachedPublishedCatalog catalog = cache.get(
                CacheNamespace.TASK_PUBLISHED_INDEX,
                "all",
                CachedPublishedCatalog.class,
                this::loadPublishedCatalog);
        return fromCatalog(catalog);
    }

    private CachedPublishedCatalog loadPublishedCatalog() {
        List<TaskDefinitionEntity> published = definitions.listPublished();
        Map<Long, String> jsonByTask = new HashMap<>();
        List<TaskVersionKey> keys = new ArrayList<>();
        for (TaskDefinitionEntity definition : published) {
            if (definition.getVersion() == null || definition.getVersion() < 1) {
                continue;
            }
            keys.add(new TaskVersionKey(definition.getId(), definition.getVersion()));
        }
        for (TaskVersionSnapshotEntity snap : snapshots.listByTaskAndVersions(keys)) {
            if (snap == null || snap.getTaskId() == null || snap.getContent() == null) {
                continue;
            }
            jsonByTask.put(snap.getTaskId(), snap.getContent());
            // Do not put TASK_SNAPSHOT here: list keeps parsed bodies in ParsedPublishedCatalog;
            // duplicating ~1k JSON strings into L1 under 1536M memcg caused heap/memcg pressure.
        }
        List<CachedPublishedCatalog.Entry> entries = new ArrayList<>(published.size());
        for (TaskDefinitionEntity definition : published) {
            if (definition.getId() == null || definition.getVersion() == null || definition.getVersion() < 1) {
                continue;
            }
            if (!jsonByTask.containsKey(definition.getId())) {
                continue;
            }
            // Metadata only — snapshot bodies parsed into ParsedPublishedCatalog (not L1 JSON copy).
            entries.add(new CachedPublishedCatalog.Entry(
                    definition.getId(), definition.getStatus(), definition.getVersion()));
        }
        return new CachedPublishedCatalog(entries);
    }

    private PublishedBundle fromCatalog(CachedPublishedCatalog catalog) {
        if (catalog == null || catalog.entries() == null || catalog.entries().isEmpty()) {
            return new PublishedBundle(List.of(), Map.of(), Map.of());
        }
        ParsedPublishedCatalog local = parsedPublishedCatalog;
        if (local != null && local.catalog() == catalog) {
            return local.bundle();
        }
        // Single-flight parse: cold L1 fill under list QPS otherwise N threads each re-parse
        // ~1MB snapshot JSON (portal-2 heap → ~1.4GiB in dropped-cut smoke).
        synchronized (this) {
            local = parsedPublishedCatalog;
            if (local != null && local.catalog() == catalog) {
                return local.bundle();
            }
            List<TaskDefinitionEntity> definitions = new ArrayList<>(catalog.entries().size());
            Map<Long, SnapshotContent> snapshots = new HashMap<>(catalog.entries().size());
            List<TaskVersionKey> keys = new ArrayList<>(catalog.entries().size());
            for (CachedPublishedCatalog.Entry entry : catalog.entries()) {
                TaskDefinitionEntity definition = new TaskDefinitionEntity();
                definition.setId(entry.id());
                definition.setStatus(entry.status());
                definition.setVersion(entry.version());
                definitions.add(definition);
                keys.add(new TaskVersionKey(entry.id(), entry.version()));
            }
            // Bulk DB read once; do not fan out into TASK_SNAPSHOT L1 (list-under-p1 mem).
            for (TaskVersionSnapshotEntity snap : this.snapshots.listByTaskAndVersions(keys)) {
                if (snap == null || snap.getTaskId() == null || snap.getContent() == null) {
                    continue;
                }
                SnapshotContent content = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
                if (content != null) {
                    snapshots.put(snap.getTaskId(), content);
                }
            }
            Map<Long, RewardPreviewView> rewards = new HashMap<>(snapshots.size());
            for (Map.Entry<Long, SnapshotContent> e : snapshots.entrySet()) {
                rewards.put(e.getKey(), SnapshotViews.rewardPreview(e.getValue()));
            }
            PublishedBundle bundle = new PublishedBundle(definitions, snapshots, rewards);
            parsedPublishedCatalog = new ParsedPublishedCatalog(catalog, bundle);
            return bundle;
        }
    }

    /** Singleflight via Caffeine {@code get(key, loader)} — coalesce concurrent loads. */
    Map<Long, TaskInstanceEntity> cachedCurrentInstancesForPublished(
            long userId,
            List<TaskDefinitionEntity> published,
            Map<Long, SnapshotContent> snapshotByTask,
            Instant now) {
        return currentInstancesByUser.get(
                userId,
                ignored -> Map.copyOf(
                        currentInstancesForPublished(userId, published, snapshotByTask, now)));
    }

    /**
     * Heap-dump relief (2026-10-12): near-ceiling histogram showed ~343k–500k
     * {@link TaskInstanceEntity} + MySQL {@code ByteArrayRow}/LocalDateTime when
     * {@code listByUser} hit seed user_id=1 (500001 rows). Prefer bounded
     * dual-IN {@code listByUserTaskIdsAndCycleKeys}; wrapped by Caffeine TTL5s
     * {@code get(key, loader)} singleflight (V9 uk plan).
     */
    private Map<Long, TaskInstanceEntity> currentInstancesForPublished(
            long userId,
            List<TaskDefinitionEntity> published,
            Map<Long, SnapshotContent> snapshotByTask,
            Instant now) {
        Map<Long, String> cycleByTask = new HashMap<>();
        // DAILY/MONTHLY share one key per request — avoid N× DateTimeFormatter allocs.
        String dailyKey = null;
        String monthlyKey = null;
        Map<String, String> cronSpecialMemo = new HashMap<>();
        for (TaskDefinitionEntity definition : published) {
            SnapshotContent snapshot = snapshotByTask.get(definition.getId());
            if (snapshot == null || definition.getId() == null) {
                continue;
            }
            String cycleType = snapshot.cycleType();
            String key;
            if (cycleType == null || CycleTypes.NONE.equals(cycleType)) {
                key = CycleTypes.NONE;
            } else if (CycleTypes.DAILY.equals(cycleType)) {
                if (dailyKey == null) {
                    dailyKey = CycleKeyResolver.resolve(
                            cycleType, snapshot.cronExpr(), snapshot.specialStart(), snapshot.specialEnd(), now);
                }
                key = dailyKey;
            } else if (CycleTypes.MONTHLY.equals(cycleType)) {
                if (monthlyKey == null) {
                    monthlyKey = CycleKeyResolver.resolve(
                            cycleType, snapshot.cronExpr(), snapshot.specialStart(), snapshot.specialEnd(), now);
                }
                key = monthlyKey;
            } else {
                String memoKey =
                        cycleType
                                + '|'
                                + nullToEmpty(snapshot.cronExpr())
                                + '|'
                                + instantKey(snapshot.specialStart())
                                + '|'
                                + instantKey(snapshot.specialEnd());
                key = cronSpecialMemo.computeIfAbsent(
                        memoKey,
                        ignored -> CycleKeyResolver.resolve(
                                cycleType,
                                snapshot.cronExpr(),
                                snapshot.specialStart(),
                                snapshot.specialEnd(),
                                now));
            }
            cycleByTask.put(definition.getId(), key);
        }
        if (cycleByTask.isEmpty()) {
            return Map.of();
        }
        // Dual-IN (task_id IN + cycle_key IN + Java pair filter). Behind Caffeine TTL3s
        // singleflight. Keep V9 (idx_user_cycle dropped). Forbid oneshot-only / pair128 /
        // cycle_key-only / FORCE / dual-IN×128.
        List<Long> taskIds = new ArrayList<>(cycleByTask.keySet());
        List<String> cycleKeys = new ArrayList<>(new java.util.LinkedHashSet<>(cycleByTask.values()));
        Map<Long, TaskInstanceEntity> out = new HashMap<>();
        for (TaskInstanceEntity row : instances.listByUserTaskIdsAndCycleKeys(userId, taskIds, cycleKeys)) {
            if (row == null || row.getTaskId() == null) {
                continue;
            }
            String want = cycleByTask.get(row.getTaskId());
            if (want != null && want.equals(row.getCycleKey())) {
                out.putIfAbsent(row.getTaskId(), row);
            }
        }
        return out;
    }

    private Map<Long, SnapshotContent> loadPublishedSnapshotsFromStore(List<TaskDefinitionEntity> published) {
        List<TaskVersionKey> keys = new ArrayList<>();
        for (TaskDefinitionEntity definition : published) {
            if (definition.getVersion() == null || definition.getVersion() < 1) {
                continue;
            }
            keys.add(new TaskVersionKey(definition.getId(), definition.getVersion()));
        }
        Map<Long, SnapshotContent> out = new HashMap<>();
        for (TaskVersionSnapshotEntity snap : snapshots.listByTaskAndVersions(keys)) {
            if (snap == null || snap.getTaskId() == null || snap.getContent() == null) {
                continue;
            }
            SnapshotContent content = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
            if (content != null) {
                out.put(snap.getTaskId(), content);
            }
        }
        return out;
    }

    record PublishedBundle(
            List<TaskDefinitionEntity> definitions,
            Map<Long, SnapshotContent> snapshots,
            Map<Long, RewardPreviewView> rewardPreviews) {}

    record ParsedPublishedCatalog(CachedPublishedCatalog catalog, PublishedBundle bundle) {}

    private static Map<Long, RewardPreviewView> rewardPreviewsOf(Map<Long, SnapshotContent> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }
        Map<Long, RewardPreviewView> out = new HashMap<>(snapshots.size());
        for (Map.Entry<Long, SnapshotContent> e : snapshots.entrySet()) {
            out.put(e.getKey(), SnapshotViews.rewardPreview(e.getValue()));
        }
        return out;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String instantKey(Instant value) {
        return value == null ? "" : Long.toString(value.toEpochMilli());
    }
}
