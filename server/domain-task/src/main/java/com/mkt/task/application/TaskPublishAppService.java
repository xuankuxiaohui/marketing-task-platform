package com.mkt.task.application;

import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.CycleKeyResolver;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.domain.ExpireAtCalculator;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.response.PublishCheckError;
import com.mkt.task.response.PublishResponse;
import com.mkt.task.response.ScheduleFailureView;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskVersionView;
import com.mkt.task.response.VersionDiffResponse;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskOperator;
import com.mkt.task.support.TaskSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@Service
public class TaskPublishAppService {

    private static final Logger log = LoggerFactory.getLogger(TaskPublishAppService.class);
    private static final int SCAN_BATCH = 100;
    private static final int OFFLINE_BATCH = 100;
    static final String SCHEDULE_FAILURE_ACTION = "schedule-publish-failure";
    private final ConcurrentHashMap<Long, String> lastFailureFingerprint = new ConcurrentHashMap<>();

    private final TaskDefinitionStore definitions;
    private final TaskMutexGroupStore mutexGroups;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskDefinitionAppService definitionsApp;
    private final PrizeEnabledLookup prizes;
    private final TaskInstanceStore instances;
    private final TaskSettings settings;
    private final Clock clock;
    private final ObjectProvider<PlatformCache> cache;
    private final ObjectProvider<TaskAuditAppender> audits;
    private final ObjectProvider<AlertWebhook> alerts;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final ObjectProvider<PlatformTransactionManager> txm;
    private final PlatformCache cacheDirect;
    private final TaskAuditAppender auditsDirect;
    private final AlertWebhook alertsDirect;
    private final JdbcTemplate jdbcDirect;
    private final TransactionTemplate txDirect;
    private final TaskPublishChecks publishChecks;
    private final TaskPublishVersionDiff versionDiff;

    @Autowired
    public TaskPublishAppService(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            PrizeEnabledLookup prizes,
            TaskInstanceStore instances,
            TaskSettings settings,
            Clock clock,
            ObjectProvider<PlatformCache> cache,
            ObjectProvider<TaskAuditAppender> audits,
            ObjectProvider<AlertWebhook> alerts,
            ObjectProvider<JdbcTemplate> jdbc,
            ObjectProvider<PlatformTransactionManager> txm,
            TaskPublishChecks publishChecks,
            TaskPublishVersionDiff versionDiff) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
        this.prizes = prizes;
        this.instances = instances;
        this.settings = settings;
        this.clock = clock;
        this.cache = cache;
        this.audits = audits;
        this.alerts = alerts;
        this.jdbc = jdbc;
        this.txm = txm;
        this.cacheDirect = null;
        this.auditsDirect = null;
        this.alertsDirect = null;
        this.jdbcDirect = null;
        this.txDirect = null;
        this.publishChecks = publishChecks;
        this.versionDiff = versionDiff;
    }

    public TaskPublishAppService(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            PrizeEnabledLookup prizes,
            TaskInstanceStore instances,
            TaskSettings settings,
            Clock clock,
            PlatformCache cache,
            TaskAuditAppender audits,
            AlertWebhook alerts,
            JdbcTemplate jdbc,
            TransactionTemplate tx) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
        this.prizes = prizes;
        this.instances = instances;
        this.settings = settings;
        this.clock = clock;
        this.cache = null;
        this.audits = null;
        this.alerts = null;
        this.jdbc = null;
        this.txm = null;
        this.cacheDirect = cache;
        this.auditsDirect = audits;
        this.alertsDirect = alerts;
        this.jdbcDirect = jdbc;
        this.txDirect = tx;
        this.publishChecks = new TaskPublishChecks(definitions, definitionsApp, prizes);
        this.versionDiff = new TaskPublishVersionDiff(snapshots);
    }

    @Transactional
    public PublishResponse publish(long id, PublishCommand command) {
        PublishCommand cmd = command == null ? new PublishCommand(null, null) : command;
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (pending(entity) && !cmd.confirmTrue()) {
            return PublishResponse.preview(
                    entity.getId(),
                    entity.getCode(),
                    versionOf(entity),
                    entity.getStatus(),
                    definitions.countInProgressInstances(id));
        }
        String status = entity.getStatus();
        if (DefinitionStatuses.DRAFT.equals(status)) {
            publishChecks.rejectIfInvalid(entity);
            requireCas(freezeToPublished(entity, false));
        } else if (DefinitionStatuses.SCHEDULED.equals(status)) {
            if (cmd.earlyTrue()) {
                publishChecks.rejectIfInvalid(entity);
                requireCas(freezeToPublished(entity, false));
            } else {
                entity.setPendingRevision(0);
                entity.setUpdatedAt(nowUtc());
                definitions.update(entity);
            }
        } else if (DefinitionStatuses.PUBLISHED.equals(status)) {
            if (!pending(entity)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "无待发布修订");
            }
            publishChecks.rejectIfInvalid(entity);
            requireCas(freezeToPublished(entity, true));
        } else {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "当前状态不可发布");
        }
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    @Transactional
    public PublishResponse schedule(long id, ScheduleCommand command) {
        if (command == null || command.publishAt() == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "publishAt 必填");
        }
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (!DefinitionStatuses.DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅草稿可设定时发布");
        }
        LocalDateTime publishAt = TaskTime.toUtc(command.publishAt());
        if (!publishAt.isAfter(nowUtc())) {
            throw publishChecks.validateFailed(List.of(new PublishCheckError("schedule", "定时发布时间必须晚于当前时间")));
        }
        entity.setSchedulePublishAt(publishAt);
        publishChecks.rejectIfInvalid(entity);
        entity.setStatus(DefinitionStatuses.SCHEDULED);
        entity.setPendingRevision(0);
        entity.setUpdatedAt(nowUtc());
        definitions.update(entity);
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    @Transactional
    public PublishResponse cancelSchedule(long id) {
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (!DefinitionStatuses.SCHEDULED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅定时任务可取消定时");
        }
        entity.setStatus(DefinitionStatuses.DRAFT);
        entity.setSchedulePublishAt(null);
        entity.setPendingRevision(0);
        entity.setUpdatedAt(nowUtc());
        definitions.update(entity);
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    @Transactional
    public PublishResponse offline(long id) {
        TaskDefinitionEntity entity = requireLockedLive(id);
        if (!DefinitionStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅已发布任务可下线");
        }
        LocalDateTime now = nowUtc();
        entity.setStatus(DefinitionStatuses.OFFLINE);
        entity.setOfflineAt(now);
        entity.setUpdatedAt(now);
        definitions.update(entity);
        recomputeInProgressExpiry(id, now);
        evictPublishedIndex();
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    private void recomputeInProgressExpiry(long taskId, LocalDateTime offlineAt) {
        Instant offline = TaskTime.toInstant(offlineAt);
        int days = settings.expireAfterWindowDays();
        long afterId = 0L;
        List<TaskInstanceEntity> batch;
        do {
            batch = instances.listInProgressByTaskAfterId(taskId, afterId, OFFLINE_BATCH);
            List<Long> snapshotIds = batch.stream()
                    .map(TaskInstanceEntity::getSnapshotId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, SnapshotContent> contents = new LinkedHashMap<>(snapshotIds.size());
            for (TaskVersionSnapshotEntity snapshot : snapshots.listByIds(snapshotIds)) {
                contents.put(snapshot.getId(), JsonUtil.fromJson(snapshot.getContent(), SnapshotContent.class));
            }
            for (TaskInstanceEntity instance : batch) {
                afterId = instance.getId();
                LocalDateTime expectedExpiry = instance.getExpireAt();
                if (expectedExpiry != null && !expectedExpiry.isAfter(offlineAt)) {
                    continue;
                }
                SnapshotContent content = contents.get(instance.getSnapshotId());
                Instant startedAt = TaskTime.toInstant(instance.getStartedAt());
                if (content == null || startedAt == null) {
                    throw new BusinessException(CommonErrorCodes.SERVER_ERROR, "实例快照或开始时间缺失");
                }
                Instant cycleEnd = CycleKeyResolver.cycleEndForKey(
                        content.cycleType(), content.cronExpr(), content.specialEnd(), instance.getCycleKey());
                LocalDateTime expiry = TaskTime.toUtc(ExpireAtCalculator.compute(
                        content.endTime(), offline, cycleEnd, startedAt, days));
                if (Objects.equals(expectedExpiry, expiry)) {
                    continue;
                }
                int affected = instances.updateExpireAtCas(instance.getId(), expectedExpiry, expiry, offlineAt);
                if (affected != 1) {
                    TaskInstanceEntity fresh = instances.getByIdForUpdate(instance.getId());
                    if (fresh != null && InstanceStatuses.IN_PROGRESS.equals(fresh.getStatus())
                            && (fresh.getExpireAt() == null || fresh.getExpireAt().isAfter(offlineAt))) {
                        throw new BusinessException(CommonErrorCodes.SERVER_ERROR, "实例期限更新冲突，请重试");
                    }
                }
            }
        } while (batch.size() == OFFLINE_BATCH);
    }

    @Transactional
    public PublishResponse resetRevision(long id) {
        TaskDefinitionEntity entity = requireLockedLive(id);
        int version = versionOf(entity);
        if (version < 1) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "没有可重置的版本快照");
        }
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(id, version);
        if (snap == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND, "版本快照不存在");
        }
        SnapshotContent content = JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
        Long mutexId = null;
        if (content.mutexGroupCode() != null && !content.mutexGroupCode().isBlank()) {
            TaskMutexGroupEntity group = mutexGroups.getByCode(content.mutexGroupCode());
            if (group == null) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "快照互斥组不存在");
            }
            mutexId = group.getId();
        }
        TaskDefinitionSaveCommand command = TaskDefinitionConvert.toSaveCommand(id, content, mutexId);
        definitionsApp.restoreEditState(id, command);
        entity = requireLive(id);
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    public List<BatchItemResponse> batchPublish(List<Long> ids) {
        return batch(ids, id -> publish(id, new PublishCommand(true, false)));
    }

    public List<BatchItemResponse> batchOffline(List<Long> ids) {
        return batch(ids, this::offline);
    }

    public List<TaskVersionView> versions(long id) {
        requireLive(id);
        return versionDiff.listVersions(id);
    }

    public VersionDiffResponse diff(long id, Integer left, Integer right) {
        requireLive(id);
        if (left == null || right == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "left 与 right 必填");
        }
        return versionDiff.diff(id, left, right);
    }

    public PageData<ScheduleFailureView> scheduleFailures(PageQuery page) {
        JdbcTemplate db = jdbc();
        if (db == null) {
            return new PageData<>(0, List.of());
        }
        PageQuery query = page == null ? PageQuery.of(1, 20) : page;
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_log WHERE module = ? AND action = ?",
                Long.class,
                "task",
                SCHEDULE_FAILURE_ACTION);
        List<ScheduleFailureView> rows = db.query(
                """
                SELECT id, request_summary, error_message, created_at
                FROM sys_audit_log
                WHERE module = ? AND action = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> failureView(
                        rs.getLong("id"),
                        rs.getString("request_summary"),
                        rs.getString("error_message"),
                        rs.getTimestamp("created_at")),
                "task",
                SCHEDULE_FAILURE_ACTION,
                query.pageSize(),
                query.offset());
        return new PageData<>(total == null ? 0L : total, rows);
    }

    public int scanDue() {
        LocalDateTime now = nowUtc();
        List<TaskDefinitionEntity> due = definitions.listDueScheduled(now, SCAN_BATCH);
        int published = 0;
        for (TaskDefinitionEntity row : due) {
            try {
                if (runInTx(() -> publishDue(row.getId()))) {
                    lastFailureFingerprint.remove(row.getId());
                    published++;
                }
            } catch (BusinessException ex) {
                recordScheduleFailure(row, ex.getMessage(), TaskPublishChecks.checkErrorsOf(ex.data()));
            } catch (RuntimeException ex) {
                log.error("sched:publish-scan failed, taskId={}", row.getId(), ex);
                recordScheduleFailure(
                        row,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                        List.of());
            }
        }
        return published;
    }

    @Transactional
    public boolean publishDue(long id) {
        TaskDefinitionEntity entity = definitions.getByIdForUpdate(id);
        if (entity == null || entity.deletedFlag()) {
            return false;
        }
        if (!DefinitionStatuses.SCHEDULED.equals(entity.getStatus())) {
            return false;
        }
        LocalDateTime now = nowUtc();
        if (entity.getSchedulePublishAt() == null || entity.getSchedulePublishAt().isAfter(now)) {
            return false;
        }
        publishChecks.rejectIfInvalid(entity);
        return freezeToPublished(entity, false);
    }

    private boolean freezeToPublished(TaskDefinitionEntity entity, boolean stayPublished) {
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

    private List<BatchItemResponse> batch(List<Long> ids, IdAction action) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "ids 不能为空");
        }
        if (ids.size() > 50) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "单批最多 50 条");
        }
        List<BatchItemResponse> out = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) {
                out.add(BatchItemResponse.fail(0, CommonErrorCodes.PARAM_INVALID.code()));
                continue;
            }
            try {
                runInTx(() -> action.run(id));
                out.add(BatchItemResponse.ok(id));
            } catch (BusinessException ex) {
                out.add(BatchItemResponse.fail(id, ex.errorCode().code()));
            }
        }
        return out;
    }

    private void recordScheduleFailure(
            TaskDefinitionEntity row, String reason, List<PublishCheckError> checkErrors) {
        String message = reason == null || reason.isBlank()
                ? TaskErrorCodes.PUBLISH_VALIDATE_FAILED.message()
                : reason;
        String fingerprint = failureAuditText(message, checkErrors);
        String previous = lastFailureFingerprint.put(row.getId(), fingerprint);
        if (fingerprint.equals(previous)) {
            return;
        }
        try {
            runInTx(() -> {
                TaskAuditAppender appender = audits();
                if (appender != null) {
                    appender.schedulePublishFailure(row.getId(), row.getCode(), message, checkErrors);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("schedule-publish-failure audit failed, taskId={}", row.getId(), ex);
        }
        AlertWebhook webhook = alerts();
        if (webhook != null) {
            webhook.notifySchedulePublishFailure(row.getId(), row.getCode(), failureAuditText(message, checkErrors));
        }
    }

    private static String failureAuditText(String reason, List<PublishCheckError> checkErrors) {
        if (checkErrors != null && !checkErrors.isEmpty()) {
            return JsonUtil.toJson(checkErrors);
        }
        return reason;
    }

    private void runInTx(Runnable action) {
        runInTx(() -> {
            action.run();
            return true;
        });
    }

    private boolean runInTx(BooleanSupplier action) {
        TransactionTemplate template = tx();
        if (template != null) {
            Boolean result = template.execute(status -> action.getAsBoolean());
            return Boolean.TRUE.equals(result);
        }
        return action.getAsBoolean();
    }

    private PlatformCache cache() {
        return cacheDirect != null ? cacheDirect : (cache == null ? null : cache.getIfAvailable());
    }

    private TaskAuditAppender audits() {
        return auditsDirect != null ? auditsDirect : (audits == null ? null : audits.getIfAvailable());
    }

    private AlertWebhook alerts() {
        return alertsDirect != null ? alertsDirect : (alerts == null ? null : alerts.getIfAvailable());
    }

    private JdbcTemplate jdbc() {
        return jdbcDirect != null ? jdbcDirect : (jdbc == null ? null : jdbc.getIfAvailable());
    }

    private TransactionTemplate tx() {
        if (txDirect != null) {
            return txDirect;
        }
        if (txm == null) {
            return null;
        }
        PlatformTransactionManager manager = txm.getIfAvailable();
        return manager == null ? null : new TransactionTemplate(manager);
    }

    static ScheduleFailureView failureView(long id, String summary, String error, Timestamp created) {
        Long taskId = null;
        String code = null;
        String reason = error;
        if (summary != null && !summary.isBlank()) {
            try {
                JsonNode node = JsonUtil.readTree(summary);
                if (node.get("taskId") != null && node.get("taskId").isNumber()) {
                    taskId = node.get("taskId").asLong();
                }
                if (node.get("code") != null && !node.get("code").isNull()) {
                    code = node.get("code").asString();
                }
                JsonNode checkErrors = node.get("checkErrors");
                if (checkErrors != null && checkErrors.isArray() && checkErrors.size() > 0) {
                    reason = JsonUtil.toJson(checkErrors);
                }
            } catch (RuntimeException ignored) {
                // keep raw error
            }
        }
        Instant at = created == null ? null : created.toInstant();
        return new ScheduleFailureView(id, taskId, code, reason, at);
    }

    private void evictPublishedIndex() {
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

    private TaskDefinitionEntity requireLive(long id) {
        TaskDefinitionEntity existing = definitions.getById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private TaskDefinitionEntity requireLockedLive(long id) {
        TaskDefinitionEntity existing = definitions.getByIdForUpdate(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void requireCas(boolean applied) {
        if (!applied) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "发布冲突，请重试");
        }
    }

    private static boolean pending(TaskDefinitionEntity entity) {
        return entity.getPendingRevision() != null && entity.getPendingRevision() == 1;
    }

    private static int versionOf(TaskDefinitionEntity entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    @FunctionalInterface
    private interface IdAction {
        void run(long id);
    }
}
