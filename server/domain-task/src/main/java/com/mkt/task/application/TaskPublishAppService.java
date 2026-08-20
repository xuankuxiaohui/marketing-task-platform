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
import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskFilterCommand;
import com.mkt.task.command.TaskGrayCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskDefinitionConvert;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.domain.GraphEdge;
import com.mkt.task.domain.GraphStep;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.domain.TaskGraphValidator;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.expression.ExpressionCompileException;
import com.mkt.task.expression.ExpressionEngine;
import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.response.DiffEntry;
import com.mkt.task.response.PublishCheckError;
import com.mkt.task.response.PublishCheckResponse;
import com.mkt.task.response.PublishResponse;
import com.mkt.task.response.ScheduleFailureView;
import com.mkt.task.response.TaskDefinitionAggregateResponse;
import com.mkt.task.response.TaskVersionView;
import com.mkt.task.response.VersionDiffResponse;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskOperator;
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
    static final String SCHEDULE_FAILURE_ACTION = "schedule-publish-failure";

    private final TaskDefinitionStore definitions;
    private final TaskMutexGroupStore mutexGroups;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskDefinitionAppService definitionsApp;
    private final PrizeEnabledLookup prizes;
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

    @Autowired
    public TaskPublishAppService(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            PrizeEnabledLookup prizes,
            Clock clock,
            ObjectProvider<PlatformCache> cache,
            ObjectProvider<TaskAuditAppender> audits,
            ObjectProvider<AlertWebhook> alerts,
            ObjectProvider<JdbcTemplate> jdbc,
            ObjectProvider<PlatformTransactionManager> txm) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
        this.prizes = prizes;
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
    }

    public TaskPublishAppService(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            PrizeEnabledLookup prizes,
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
    }

    @Transactional
    public PublishResponse publish(long id, PublishCommand command) {
        PublishCommand cmd = command == null ? new PublishCommand(null, null) : command;
        TaskDefinitionEntity entity = requireLive(id);
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
            rejectIfInvalid(entity, false);
            freezeToPublished(entity, false);
        } else if (DefinitionStatuses.SCHEDULED.equals(status)) {
            if (cmd.earlyTrue()) {
                rejectIfInvalid(entity, true);
                freezeToPublished(entity, false);
            } else {
                entity.setPendingRevision(0);
                entity.setUpdatedAt(nowUtc());
                definitions.update(entity);
            }
        } else if (DefinitionStatuses.PUBLISHED.equals(status)) {
            if (!pending(entity)) {
                throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "无待发布修订");
            }
            rejectIfInvalid(entity, false);
            freezeToPublished(entity, true);
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
        TaskDefinitionEntity entity = requireLive(id);
        if (!DefinitionStatuses.DRAFT.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅草稿可设定时发布");
        }
        LocalDateTime publishAt = TaskTime.toUtc(command.publishAt());
        if (!publishAt.isAfter(nowUtc())) {
            throw validateFailed(List.of(new PublishCheckError("schedule", "定时发布时间必须晚于当前时间")));
        }
        entity.setSchedulePublishAt(publishAt);
        rejectIfInvalid(entity, true);
        entity.setStatus(DefinitionStatuses.SCHEDULED);
        entity.setPendingRevision(0);
        entity.setUpdatedAt(nowUtc());
        definitions.update(entity);
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    @Transactional
    public PublishResponse cancelSchedule(long id) {
        TaskDefinitionEntity entity = requireLive(id);
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
        TaskDefinitionEntity entity = requireLive(id);
        if (!DefinitionStatuses.PUBLISHED.equals(entity.getStatus())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "仅已发布任务可下线");
        }
        LocalDateTime now = nowUtc();
        entity.setStatus(DefinitionStatuses.OFFLINE);
        entity.setOfflineAt(now);
        entity.setUpdatedAt(now);
        definitions.update(entity);
        evictPublishedIndex();
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
    }

    @Transactional
    public PublishResponse resetRevision(long id) {
        TaskDefinitionEntity entity = requireLive(id);
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
        return snapshots.listByTaskId(id).stream()
                .map(row -> new TaskVersionView(
                        row.getVersion(), TaskTime.toInstant(row.getPublishedAt()), row.getPublishedBy()))
                .toList();
    }

    public VersionDiffResponse diff(long id, Integer left, Integer right) {
        requireLive(id);
        if (left == null || right == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "left 与 right 必填");
        }
        SnapshotContent leftContent = requireSnapshot(id, left);
        SnapshotContent rightContent = requireSnapshot(id, right);
        return new VersionDiffResponse(
                diffByKey(indexSteps(leftContent.steps()), indexSteps(rightContent.steps())),
                diffByKey(indexEdges(leftContent.transitions()), indexEdges(rightContent.transitions())),
                diffByKey(indexFilter(leftContent.filter()), indexFilter(rightContent.filter())),
                diffByKey(indexGray(leftContent.gray()), indexGray(rightContent.gray())),
                diffByKey(indexActions(leftContent.actions()), indexActions(rightContent.actions())));
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
                (rs, rowNum) -> toFailureView(
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
                runInTx(() -> publishDue(row.getId()));
                published++;
            } catch (BusinessException ex) {
                recordScheduleFailure(row, ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("sched:publish-scan failed, taskId={}", row.getId(), ex);
                recordScheduleFailure(row, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }
        return published;
    }

    @Transactional
    public void publishDue(long id) {
        TaskDefinitionEntity entity = definitions.getByIdForUpdate(id);
        if (entity == null || entity.deletedFlag()) {
            return;
        }
        if (!DefinitionStatuses.SCHEDULED.equals(entity.getStatus())) {
            return;
        }
        LocalDateTime now = nowUtc();
        if (entity.getSchedulePublishAt() == null || entity.getSchedulePublishAt().isAfter(now)) {
            return;
        }
        rejectIfInvalid(entity, true);
        freezeToPublished(entity, false);
    }

    private void freezeToPublished(TaskDefinitionEntity entity, boolean stayPublished) {
        LocalDateTime now = nowUtc();
        int nextVersion = versionOf(entity) + 1;
        insertSnapshot(entity, nextVersion, now);
        entity.setVersion(nextVersion);
        entity.setStatus(DefinitionStatuses.PUBLISHED);
        entity.setPendingRevision(0);
        entity.setSchedulePublishAt(null);
        entity.setUpdatedAt(now);
        definitions.update(entity);
        evictPublishedIndex();
        cacheSnapshot(entity.getId(), nextVersion);
        if (stayPublished) {
            log.info("published revision, taskId={}, version={}", entity.getId(), nextVersion);
        } else {
            log.info("published task, taskId={}, version={}", entity.getId(), nextVersion);
        }
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

    private void rejectIfInvalid(TaskDefinitionEntity entity, boolean scheduled) {
        List<PublishCheckError> errors = checkPublish(entity, scheduled);
        if (!errors.isEmpty()) {
            throw validateFailed(errors);
        }
    }

    private List<PublishCheckError> checkPublish(TaskDefinitionEntity entity, boolean scheduled) {
        List<PublishCheckError> errors = new ArrayList<>();
        TaskDefinitionAggregateResponse view = definitionsApp.get(entity.getId());
        List<TaskStepCommand> steps = view.steps() == null ? List.of() : view.steps();
        List<TaskTransitionCommand> edges = view.transitions() == null ? List.of() : view.transitions();
        if (steps.isEmpty()) {
            errors.add(new PublishCheckError("steps", "至少需要一个步骤"));
        } else {
            List<GraphStep> graphSteps = steps.stream().map(s -> new GraphStep(s.code(), s.seq())).toList();
            List<GraphEdge> graphEdges = edges.stream()
                    .map(e -> new GraphEdge(e.fromStepCode(), e.toStepCode()))
                    .toList();
            if (!TaskGraphValidator.reachableAndOpen(graphSteps, graphEdges)) {
                errors.add(new PublishCheckError("reachability", "存在不可达或死锁步骤"));
            }
        }
        for (TaskStepCommand step : steps) {
            if (StepTypes.REWARD.equals(step.type())) {
                if (step.prizeId() == null || !prizes.enabled(step.prizeId())) {
                    errors.add(new PublishCheckError("reward:" + step.code(), "未引用启用奖品"));
                }
            }
        }
        if (view.filter() != null && view.filter().expr() != null && !view.filter().expr().isBlank()) {
            try {
                ExpressionEngine.requireValid(view.filter().expr());
            } catch (ExpressionCompileException ex) {
                errors.add(new PublishCheckError("expression", ex.detail().reason()));
            }
        }
        for (TaskTransitionCommand edge : edges) {
            if (edge.conditionExpr() == null || edge.conditionExpr().isBlank()) {
                continue;
            }
            try {
                ExpressionEngine.requireValid(edge.conditionExpr());
            } catch (ExpressionCompileException ex) {
                errors.add(new PublishCheckError("expression", ex.detail().reason()));
            }
        }
        if (entity.getStartTime() != null
                && entity.getEndTime() != null
                && !entity.getStartTime().isBefore(entity.getEndTime())) {
            errors.add(new PublishCheckError("time-window", "开始必须早于结束"));
        }
        if (scheduled
                && entity.getSchedulePublishAt() != null
                && entity.getEndTime() != null
                && !entity.getSchedulePublishAt().isBefore(entity.getEndTime())) {
            errors.add(new PublishCheckError("schedule", "定时发布时间必须早于时间窗结束"));
        }
        return errors;
    }

    private BusinessException validateFailed(List<PublishCheckError> errors) {
        return new BusinessException(
                TaskErrorCodes.PUBLISH_VALIDATE_FAILED,
                TaskErrorCodes.PUBLISH_VALIDATE_FAILED.message(),
                new PublishCheckResponse(List.copyOf(errors)));
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

    private void recordScheduleFailure(TaskDefinitionEntity row, String reason) {
        String message = reason == null || reason.isBlank() ? TaskErrorCodes.PUBLISH_VALIDATE_FAILED.message() : reason;
        try {
            runInTx(() -> {
                TaskAuditAppender appender = audits();
                if (appender != null) {
                    appender.schedulePublishFailure(row.getId(), row.getCode(), message);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("schedule-publish-failure audit failed, taskId={}", row.getId(), ex);
        }
        AlertWebhook webhook = alerts();
        if (webhook != null) {
            webhook.notifySchedulePublishFailure(row.getId(), row.getCode(), message);
        }
    }

    private void runInTx(Runnable action) {
        TransactionTemplate template = tx();
        if (template != null) {
            template.executeWithoutResult(status -> action.run());
            return;
        }
        action.run();
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

    private SnapshotContent requireSnapshot(long taskId, int version) {
        TaskVersionSnapshotEntity snap = snapshots.getByTaskAndVersion(taskId, version);
        if (snap == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND, "版本快照不存在");
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    private ScheduleFailureView toFailureView(long id, String summary, String error, Timestamp created) {
        Long taskId = null;
        String code = null;
        if (summary != null && !summary.isBlank()) {
            try {
                JsonNode node = JsonUtil.readTree(summary);
                if (node.get("taskId") != null && node.get("taskId").isNumber()) {
                    taskId = node.get("taskId").asLong();
                }
                if (node.get("code") != null && !node.get("code").isNull()) {
                    code = node.get("code").asString();
                }
            } catch (RuntimeException ignored) {
                // keep raw error
            }
        }
        Instant at = created == null ? null : created.toInstant();
        return new ScheduleFailureView(id, taskId, code, error, at);
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
            platformCache.put(CacheNamespace.TASK_SNAPSHOT, taskId + ":" + version, snap.getContent());
        }
    }

    private TaskDefinitionEntity requireLive(long id) {
        TaskDefinitionEntity existing = definitions.getById(id);
        if (existing == null || existing.deletedFlag()) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return existing;
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

    private static Map<String, Object> indexSteps(List<TaskStepCommand> steps) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (steps == null) {
            return map;
        }
        for (TaskStepCommand step : steps) {
            map.put(step.code(), step);
        }
        return map;
    }

    private static Map<String, Object> indexEdges(List<TaskTransitionCommand> edges) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (edges == null) {
            return map;
        }
        for (TaskTransitionCommand edge : edges) {
            map.put(edge.fromStepCode() + "->" + edge.toStepCode(), edge);
        }
        return map;
    }

    private static Map<String, Object> indexFilter(TaskFilterCommand filter) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (filter == null) {
            return map;
        }
        map.put("expr", filter.expr());
        map.put("allowCrowdIds", filter.allowCrowdIds());
        map.put("excludeCrowdIds", filter.excludeCrowdIds());
        return map;
    }

    private static Map<String, Object> indexGray(TaskGrayCommand gray) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (gray == null) {
            return map;
        }
        map.put("type", gray.type());
        map.put("ratio", gray.ratio());
        map.put("abGroup", gray.abGroup());
        map.put("crowdId", gray.crowdId());
        map.put("excludeCrowdId", gray.excludeCrowdId());
        return map;
    }

    private static Map<String, Object> indexActions(List<TaskActionCommand> actions) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (actions == null) {
            return map;
        }
        for (TaskActionCommand action : actions) {
            String key = action.scope() + "|" + action.platform()
                    + (action.stepCode() == null ? "" : "|" + action.stepCode());
            map.put(key, action);
        }
        return map;
    }

    private static List<DiffEntry> diffByKey(Map<String, Object> left, Map<String, Object> right) {
        List<DiffEntry> out = new ArrayList<>();
        for (String key : left.keySet()) {
            if (!right.containsKey(key)) {
                out.add(new DiffEntry("REMOVE", key, left.get(key), null));
            } else if (!sameJson(left.get(key), right.get(key))) {
                out.add(new DiffEntry("CHANGE", key, left.get(key), right.get(key)));
            }
        }
        for (String key : right.keySet()) {
            if (!left.containsKey(key)) {
                out.add(new DiffEntry("ADD", key, null, right.get(key)));
            }
        }
        return out;
    }

    private static boolean sameJson(Object left, Object right) {
        return Objects.equals(JsonUtil.toJson(left), JsonUtil.toJson(right));
    }

    @FunctionalInterface
    private interface IdAction {
        void run(long id);
    }
}
