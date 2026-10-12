package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.CycleTypes;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.domain.VisibilityEvaluator;
import com.mkt.task.domain.VisibilityResult;
import com.mkt.task.domain.CrowdMembership;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.expression.EvalContext;
import com.mkt.task.expression.EvalContexts;
import com.mkt.task.response.CurrentStepView;
import com.mkt.task.response.InstanceStepView;
import com.mkt.task.response.MineTaskView;
import com.mkt.task.response.TaskBriefView;
import com.mkt.task.response.TaskStepSource;
import com.mkt.task.response.CachedPublishedCatalog;
import com.mkt.task.response.RewardPreviewView;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskDetailResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.PriorityQueue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskPortalAppService {

    private final TaskDefinitionStore definitions;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskInstanceStore instances;
    private final TaskCrowdStore crowds;
    private final UserAttributePort users;
    private final RiskCheckPort risk;
    private final Clock clock;
    private final PlatformCache cache;
    /** Process-local reuse of parsed snapshots while L1 returns the same catalog instance. */
    private volatile ParsedPublishedCatalog parsedPublishedCatalog;

    /**
     * Architect 2026-10-12: short-TTL read cache for portal list current-instances.
     * Caffeine expireAfterWrite 3s (in 2–5s band). Keyed by userId — cycle keys stable
     * within TTL. Miss → one-shot tuple IN (V9 uk). Caps: 1536M not raised.
     */
    private static final Duration CURRENT_INSTANCES_TTL = Duration.ofSeconds(3);

    private final Cache<Long, Map<Long, TaskInstanceEntity>> currentInstancesByUser =
            Caffeine.newBuilder()
                    .expireAfterWrite(CURRENT_INSTANCES_TTL)
                    .maximumSize(8_192)
                    .build();

    @Autowired
    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<RiskCheckPort> risk,
            ObjectProvider<PlatformCache> cache,
            Clock clock) {
        this(
                definitions,
                snapshots,
                instances,
                crowds,
                users.getIfAvailable(),
                risk.getIfAvailable(),
                clock,
                cache.getIfAvailable());
    }

    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock) {
        this(definitions, snapshots, instances, crowds, users, risk, clock, null);
    }

    public TaskPortalAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            RiskCheckPort risk,
            Clock clock,
            PlatformCache cache) {
        this.definitions = definitions;
        this.snapshots = snapshots;
        this.instances = instances;
        this.crowds = crowds;
        this.users = users;
        this.risk = risk;
        this.clock = clock;
        this.cache = cache;
    }

    public TaskStepSource sourceForStep(long stepId) {
        TaskInstanceStepEntity step = instances.getStepById(stepId);
        if (step == null || step.getInstanceId() == null) {
            return null;
        }
        TaskInstanceEntity instance = instances.getById(step.getInstanceId());
        if (instance == null || instance.getTaskId() == null) {
            return null;
        }
        TaskVersionSnapshotEntity snap =
                instance.getSnapshotId() == null ? null : snapshots.getById(instance.getSnapshotId());
        SnapshotContent content =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        String name = content == null || content.name() == null || content.name().isBlank()
                ? instance.getTaskCode()
                : content.name();
        return new TaskStepSource(instance.getTaskId(), name);
    }

    public PageData<TaskCardView> list(Long userId, String category, Integer page, Integer pageSize) {
        if (userId != null && users == null) {
            return new PageData<>(0, List.of());
        }
        if (userId != null && blacklisted(userId)) {
            return new PageData<>(0, List.of());
        }
        UserAttributes attrs = userId == null ? null : users.attributes(userId);
        Instant now = clock.instant();
        PublishedBundle publishedBundle = publishedBundle();
        List<TaskDefinitionEntity> published = publishedBundle.definitions();
        Map<Long, SnapshotContent> snapshotByTask = publishedBundle.snapshots();
        Map<Long, TaskInstanceEntity> currentByTask =
                userId == null
                        ? Map.of()
                        : cachedCurrentInstancesForPublished(userId, published, snapshotByTask, now);
        // One membership/resolver/evalContext per list request (was N× per published task).
        CrowdMembership membership = userId == null ? null : new StoreCrowdMembership(crowds);
        CrowdResolver resolver = userId == null ? null : new StoreCrowdResolver(crowds, users, userId);
        EvalContext filterCtx = userId == null ? null : EvalContexts.filter(attrs, now, resolver);
        // Top-K pending only (offset+pageSize) — avoid ~N PendingCard + full sort under ~1k catalog.
        PageQuery query = PageQuery.of(page, pageSize);
        long offset = query.offset();
        int limit = query.pageSize();
        int retain = (int) Math.min(Integer.MAX_VALUE - 8L, offset + (long) limit);
        if (retain < 1) {
            retain = 1;
        }
        Comparator<PendingCard> asc =
                Comparator.comparingInt(PendingCard::sortWeight).thenComparingLong(PendingCard::taskId);
        PriorityQueue<PendingCard> best = new PriorityQueue<>(Math.min(retain, 64), asc.reversed());
        long total = 0;
        for (TaskDefinitionEntity definition : published) {
            SnapshotContent snapshot = snapshotByTask.get(definition.getId());
            if (snapshot == null) {
                continue;
            }
            if (category != null && !category.isBlank() && !category.equals(snapshot.category())) {
                continue;
            }
            if (userId == null) {
                if (!VisibilityEvaluator.publicCard(
                        definition.getStatus(),
                        snapshot.startTime(),
                        snapshot.endTime(),
                        snapshot.gray(),
                        snapshot.filter(),
                        now)) {
                    continue;
                }
                total++;
                considerPending(best, retain, definition, snapshot, InstanceStatuses.NOT_STARTED);
                continue;
            }
            TaskInstanceEntity current = currentByTask.get(definition.getId());
            boolean inProgressThisCycle =
                    current != null && InstanceStatuses.IN_PROGRESS.equals(current.getStatus());
            // Boolean visible() — no VisibilityResult / reasons ArrayList per published task.
            boolean visible = VisibilityEvaluator.visible(
                    definition.getStatus(),
                    snapshot.startTime(),
                    snapshot.endTime(),
                    snapshot.gray(),
                    snapshot.filter(),
                    definition.getId(),
                    userId,
                    now,
                    membership,
                    filterCtx);
            if (!visible && !inProgressThisCycle) {
                continue;
            }
            String userStatus = current == null ? InstanceStatuses.NOT_STARTED : current.getStatus();
            total++;
            considerPending(best, retain, definition, snapshot, userStatus);
        }
        List<PendingCard> ranked = new ArrayList<>(best);
        ranked.sort(asc);
        int from = (int) Math.min(offset, ranked.size());
        int to = (int) Math.min(from + (long) limit, ranked.size());
        List<TaskCardView> pageCards = new ArrayList<>(Math.max(0, to - from));
        Map<Long, RewardPreviewView> rewards = publishedBundle.rewardPreviews();
        for (int i = from; i < to; i++) {
            PendingCard row = ranked.get(i);
            pageCards.add(toCard(row.definition(), row.snapshot(), row.userStatus(), rewards));
        }
        return new PageData<>(total, pageCards);
    }

    public TaskDetailResponse detail(long taskId, Long userId, String platform) {
        TaskDefinitionEntity definition = definitions.getById(taskId);
        if (definition == null || definition.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        Instant now = clock.instant();
        if (userId == null) {
            return anonymousDetail(definition, now);
        }
        SnapshotContent live = snapshotOf(definition);
        String cycleKey = live == null
                ? CycleKeyResolver.resolve(
                        definition.getCycleType(),
                        definition.getCronExpr(),
                        TaskTime.toInstant(definition.getSpecialStart()),
                        TaskTime.toInstant(definition.getSpecialEnd()),
                        now)
                : CycleKeyResolver.resolve(
                        live.cycleType(), live.cronExpr(), live.specialStart(), live.specialEnd(), now);
        TaskInstanceEntity current = instances.getByUserTaskCycle(userId, taskId, cycleKey);
        TaskInstanceEntity inProgress = pickInProgress(taskId, userId, cycleKey);
        if (inProgress != null) {
            return inProgressDetail(inProgress, platform);
        }
        if (current != null && InstanceStatuses.terminal(current.getStatus())) {
            return new TaskDetailResponse(current.getStatus(), current.getId(), null, null, null, null, null);
        }
        SnapshotContent snapshot = snapshotOf(definition);
        UserAttributes attrs = users == null ? UserAttributes.notFound() : users.attributes(userId);
        VisibilityResult visibility =
                snapshot == null
                        ? VisibilityResult.deny(List.of())
                        : visibility(definition, snapshot, userId, attrs, now);
        if (snapshot == null || !visibility.visible()) {
            return new TaskDetailResponse(InstanceStatuses.OFFLINE, null, null, null, null, null, null);
        }
        return new TaskDetailResponse(
                InstanceStatuses.NOT_STARTED,
                null,
                new TaskBriefView(snapshot.name(), snapshot.iconUrl(), snapshot.description(), snapshot.category()),
                SnapshotViews.stepsPreview(snapshot),
                SnapshotViews.rewardPreview(snapshot),
                null,
                null);
    }

    public PageData<MineTaskView> mine(long userId, String status, String category, Integer page, Integer pageSize) {
        PageQuery query = PageQuery.of(page, pageSize);
        List<Long> categoryTaskIds = null;
        String categoryFilter = allCategoryToNull(category);
        if (categoryFilter != null) {
            categoryTaskIds = new ArrayList<>();
            PublishedBundle publishedBundle = publishedBundle();
            List<TaskDefinitionEntity> published = publishedBundle.definitions();
            Map<Long, SnapshotContent> publishedSnaps = publishedBundle.snapshots();
            for (TaskDefinitionEntity definition : published) {
                SnapshotContent snapshot = publishedSnaps.get(definition.getId());
                if (snapshot != null && categoryFilter.equals(snapshot.category())) {
                    categoryTaskIds.add(definition.getId());
                }
            }
            List<TaskInstanceEntity> history = instances.listByUser(userId);
            List<Long> snapIds = new ArrayList<>();
            for (TaskInstanceEntity row : history) {
                if (categoryTaskIds.contains(row.getTaskId()) || row.getSnapshotId() == null) {
                    continue;
                }
                snapIds.add(row.getSnapshotId());
            }
            Map<Long, SnapshotContent> historySnaps = loadSnapshotsByIds(snapIds);
            for (TaskInstanceEntity row : history) {
                if (categoryTaskIds.contains(row.getTaskId()) || row.getSnapshotId() == null) {
                    continue;
                }
                SnapshotContent content = historySnaps.get(row.getSnapshotId());
                if (content != null && categoryFilter.equals(content.category())) {
                    categoryTaskIds.add(row.getTaskId());
                }
            }
            if (categoryTaskIds.isEmpty()) {
                return new PageData<>(0, List.of());
            }
        }
        String statusFilter = InstanceStatuses.mineFilter(status);
        long total = instances.countMine(userId, statusFilter, categoryTaskIds);
        List<TaskInstanceEntity> rows =
                instances.listMine(userId, statusFilter, categoryTaskIds, query.offset(), query.pageSize());
        List<MineTaskView> views = new ArrayList<>(rows.size());
        for (TaskInstanceEntity row : rows) {
            views.add(toMine(row));
        }
        return new PageData<>(total, views);
    }

    private TaskDetailResponse anonymousDetail(TaskDefinitionEntity definition, Instant now) {
        SnapshotContent snapshot = snapshotOf(definition);
        if (snapshot == null
                || !VisibilityEvaluator.publicCard(
                        definition.getStatus(),
                        snapshot.startTime(),
                        snapshot.endTime(),
                        snapshot.gray(),
                        snapshot.filter(),
                        now)) {
            return new TaskDetailResponse(InstanceStatuses.OFFLINE, null, null, null, null, null, null);
        }
        return new TaskDetailResponse(
                InstanceStatuses.NOT_STARTED,
                null,
                new TaskBriefView(snapshot.name(), snapshot.iconUrl(), snapshot.description(), snapshot.category()),
                SnapshotViews.stepsPreview(snapshot),
                SnapshotViews.rewardPreview(snapshot),
                null,
                null);
    }

    private TaskDetailResponse inProgressDetail(TaskInstanceEntity instance, String platform) {
        TaskVersionSnapshotEntity snap = snapshots.getById(instance.getSnapshotId());
        SnapshotContent snapshot =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        List<TaskInstanceStepEntity> steps = instances.listSteps(instance.getId());
        List<InstanceStepView> stepViews = new ArrayList<>();
        CurrentStepView current = null;
        if (steps != null && snapshot != null) {
            for (TaskInstanceStepEntity step : steps) {
                TaskStepCommand def = SnapshotViews.step(snapshot, step.getStepCode());
                String name = def == null ? step.getStepCode() : def.name();
                Integer target = def == null ? null : def.progressTarget();
                stepViews.add(new InstanceStepView(
                        step.getStepCode(),
                        name,
                        step.getType(),
                        step.getStatus(),
                        step.getProgressCurrent() == null ? 0 : step.getProgressCurrent(),
                        target));
                if (current == null && StepStatuses.ACTIVE.equals(step.getStatus())) {
                    current = new CurrentStepView(
                            step.getStepCode(),
                            name,
                            step.getType(),
                            step.getProgressCurrent(),
                            target,
                            SnapshotViews.action(snapshot, step.getStepCode(), platform));
                }
            }
        }
        return new TaskDetailResponse(
                InstanceStatuses.IN_PROGRESS,
                instance.getId(),
                null,
                null,
                SnapshotViews.rewardPreview(snapshot),
                stepViews,
                current);
    }

    private VisibilityResult visibility(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            UserAttributes attrs,
            Instant now) {
        StoreCrowdMembership membership = new StoreCrowdMembership(crowds);
        CrowdResolver resolver = new StoreCrowdResolver(crowds, users, userId);
        return visibility(
                definition, snapshot, userId, attrs, now, membership, EvalContexts.filter(attrs, now, resolver));
    }

    private VisibilityResult visibility(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            long userId,
            UserAttributes attrs,
            Instant now,
            CrowdMembership membership,
            EvalContext filterCtx) {
        return VisibilityEvaluator.evaluate(
                definition.getStatus(),
                snapshot.startTime(),
                snapshot.endTime(),
                snapshot.gray(),
                snapshot.filter(),
                definition.getId(),
                userId,
                attrs,
                now,
                membership,
                filterCtx);
    }

    private SnapshotContent snapshotOf(TaskDefinitionEntity definition) {
        if (definition.getVersion() == null || definition.getVersion() < 1) {
            return null;
        }
        String json = snapshotJson(definition.getId(), definition.getVersion());
        return json == null ? null : JsonUtil.fromJson(json, SnapshotContent.class);
    }

    private String snapshotJson(long taskId, int version) {
        if (cache != null) {
            return cache.get(
                    CacheNamespace.TASK_SNAPSHOT,
                    taskId + ":" + version,
                    String.class,
                    () -> loadSnapshotJson(taskId, version));
        }
        return loadSnapshotJson(taskId, version);
    }

    private String loadSnapshotJson(long taskId, int version) {
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(taskId, version);
        return snap == null ? null : snap.getContent();
    }

    private PublishedBundle publishedBundle() {
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

    /**
     * Heap-dump relief (2026-10-12): near-ceiling histogram showed ~343k–500k
     * {@link TaskInstanceEntity} + MySQL {@code ByteArrayRow}/LocalDateTime when
     * {@code listByUser} hit seed user_id=1 (500001 rows). Bounded one-shot
     * {@code listByUserTaskCycles} — {@code (task_id, cycle_key) IN} one-shot; wrapped by
     * {@code cachedCurrentInstancesForPublished} (Caffeine TTL 3s).
     */
    private Map<Long, TaskInstanceEntity> cachedCurrentInstancesForPublished(
            long userId,
            List<TaskDefinitionEntity> published,
            Map<Long, SnapshotContent> snapshotByTask,
            Instant now) {
        Map<Long, TaskInstanceEntity> hit = currentInstancesByUser.getIfPresent(userId);
        if (hit != null) {
            return hit;
        }
        Map<Long, TaskInstanceEntity> loaded =
                currentInstancesForPublished(userId, published, snapshotByTask, now);
        Map<Long, TaskInstanceEntity> frozen = Map.copyOf(loaded);
        currentInstancesByUser.put(userId, frozen);
        return frozen;
    }

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
        // Tuple IN 单次打满 via listByUserTaskCycles (no serial ≤64 chunking).
        // Keep V9 (idx_user_cycle dropped). Forbid dual-IN×128 / pair128 / cycle_key-only / FORCE.
        List<TaskCycleKey> pairs = new ArrayList<>(cycleByTask.size());
        for (Map.Entry<Long, String> e : cycleByTask.entrySet()) {
            pairs.add(new TaskCycleKey(e.getKey(), e.getValue()));
        }
        Map<Long, TaskInstanceEntity> out = new HashMap<>(pairs.size());
        for (TaskInstanceEntity row : instances.listByUserTaskCycles(userId, pairs)) {
            if (row == null || row.getTaskId() == null) {
                continue;
            }
            out.putIfAbsent(row.getTaskId(), row);
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

    private record PublishedBundle(
            List<TaskDefinitionEntity> definitions,
            Map<Long, SnapshotContent> snapshots,
            Map<Long, RewardPreviewView> rewardPreviews) {}

    private record ParsedPublishedCatalog(CachedPublishedCatalog catalog, PublishedBundle bundle) {}

    /** Sort key only — avoids building TaskCardView/rewardPreview for every published task. */
    private record PendingCard(
            TaskDefinitionEntity definition, SnapshotContent snapshot, String userStatus) {
        int sortWeight() {
            return snapshot.sortWeight();
        }

        long taskId() {
            return definition.getId();
        }
    }

    private Map<Long, SnapshotContent> loadSnapshotsByIds(List<Long> ids) {
        Map<Long, SnapshotContent> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (TaskVersionSnapshotEntity snap : snapshots.listByIds(ids)) {
            if (snap == null || snap.getId() == null || snap.getContent() == null) {
                continue;
            }
            SnapshotContent content = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
            if (content != null) {
                out.put(snap.getId(), content);
            }
        }
        return out;
    }


    private boolean blacklisted(long userId) {
        if (risk == null) {
            return false;
        }
        return risk.onUserList(userId, RiskListType.BLACK);
    }

    private TaskInstanceEntity pickInProgress(long taskId, long userId, String cycleKey) {
        TaskInstanceEntity fallback = null;
        for (TaskInstanceEntity row : instances.listInProgressByUser(userId)) {
            if (taskId != row.getTaskId()) {
                continue;
            }
            if (cycleKey.equals(row.getCycleKey())) {
                return row;
            }
            if (fallback == null) {
                fallback = row;
            }
        }
        return fallback;
    }


    /**
     * Keep only the best {@code retain} pending cards (ascending sort key). Peek is the worst of
     * the kept set — replace when the candidate sorts strictly earlier (lower weight / taskId).
     */
    private static void considerPending(
            PriorityQueue<PendingCard> best,
            int retain,
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            String userStatus) {
        if (best.size() < retain) {
            best.offer(new PendingCard(definition, snapshot, userStatus));
            return;
        }
        PendingCard worst = best.peek();
        int cmp = Integer.compare(snapshot.sortWeight(), worst.sortWeight());
        if (cmp == 0) {
            cmp = Long.compare(definition.getId(), worst.taskId());
        }
        if (cmp < 0) {
            best.poll();
            best.offer(new PendingCard(definition, snapshot, userStatus));
        }
    }

    private static TaskCardView toCard(
            TaskDefinitionEntity definition,
            SnapshotContent snapshot,
            String userStatus,
            Map<Long, RewardPreviewView> rewards) {
        int weight = snapshot.sortWeight();
        RewardPreviewView reward =
                rewards == null
                        ? SnapshotViews.rewardPreview(snapshot)
                        : rewards.getOrDefault(definition.getId(), SnapshotViews.rewardPreview(snapshot));
        return new TaskCardView(
                definition.getId(),
                snapshot.code(),
                snapshot.name(),
                snapshot.category(),
                snapshot.iconUrl(),
                snapshot.badgeText(),
                reward,
                userStatus,
                weight);
    }

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

    private MineTaskView toMine(TaskInstanceEntity row) {
        TaskVersionSnapshotEntity snap = snapshots.getById(row.getSnapshotId());
        SnapshotContent content =
                snap == null ? null : JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        String name = content == null ? row.getTaskCode() : content.name();
        String icon = content == null ? null : content.iconUrl();
        String category = content == null ? null : content.category();
        String currentName = null;
        if (InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            List<TaskInstanceStepEntity> steps = instances.listSteps(row.getId());
            if (steps != null) {
                for (TaskInstanceStepEntity step : steps) {
                    if (StepStatuses.ACTIVE.equals(step.getStatus())) {
                        TaskStepCommand def = SnapshotViews.step(content, step.getStepCode());
                        currentName = def == null ? step.getStepCode() : def.name();
                        break;
                    }
                }
            }
        }
        return new MineTaskView(
                row.getId(),
                row.getTaskId(),
                name,
                icon,
                category,
                row.getStatus(),
                currentName,
                TaskTime.toInstant(row.getStartedAt()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String instantKey(Instant value) {
        return value == null ? "" : Long.toString(value.toEpochMilli());
    }

    private static String allCategoryToNull(String category) {
        String raw = blankToNull(category);
        if (raw == null || "ALL".equalsIgnoreCase(raw) || InstanceStatuses.tabIndexLeak(raw)) {
            return null;
        }
        return raw;
    }
}
