package com.mkt.task.application;

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
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.entity.TaskMutexGroupEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.response.PublishCheckError;
import com.mkt.task.response.PublishResponse;
import com.mkt.task.response.ScheduleFailureView;
import com.mkt.task.response.TaskVersionView;
import com.mkt.task.response.VersionDiffResponse;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskPublishAppService {

    private final TaskDefinitionStore definitions;
    private final TaskMutexGroupStore mutexGroups;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskDefinitionAppService definitionsApp;
    private final PrizeEnabledLookup prizes;
    private final Clock clock;
    private final ObjectProvider<PlatformTransactionManager> txm;
    private final TransactionTemplate txDirect;
    private final TaskPublishChecks publishChecks;
    private final TaskPublishVersionDiff versionDiff;
    private final TaskPublishFreeze publishFreeze;
    private final TaskPublishScheduleFailures scheduleFailures;
    private final PublishOfflineExpiry offlineExpiry;
    private final PublishScanDue publishScanDue;

    @Autowired
    public TaskPublishAppService(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            PrizeEnabledLookup prizes,
            Clock clock,
            ObjectProvider<PlatformTransactionManager> txm,
            TaskPublishChecks publishChecks,
            TaskPublishVersionDiff versionDiff,
            TaskPublishFreeze publishFreeze,
            TaskPublishScheduleFailures scheduleFailures,
            PublishOfflineExpiry offlineExpiry,
            PublishScanDue publishScanDue) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
        this.prizes = prizes;
        this.clock = clock;
        this.txm = txm;
        this.txDirect = null;
        this.publishChecks = publishChecks;
        this.versionDiff = versionDiff;
        this.publishFreeze = publishFreeze;
        this.scheduleFailures = scheduleFailures;
        this.offlineExpiry = offlineExpiry;
        this.publishScanDue = publishScanDue;
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
        this.clock = clock;
        this.txm = null;
        this.txDirect = tx;
        this.publishChecks = new TaskPublishChecks(definitions, definitionsApp, prizes);
        this.versionDiff = new TaskPublishVersionDiff(snapshots);
        this.publishFreeze = new TaskPublishFreeze(
                definitions, mutexGroups, snapshots, definitionsApp, clock, cache);
        this.scheduleFailures = new TaskPublishScheduleFailures(audits, alerts, jdbc, tx);
        this.offlineExpiry = new PublishOfflineExpiry(instances, snapshots, settings);
        this.publishScanDue = new PublishScanDue(
                definitions, this.publishChecks, this.publishFreeze, this.scheduleFailures, clock, tx);
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
            requireCas(publishFreeze.freezeToPublished(entity, false));
        } else if (DefinitionStatuses.SCHEDULED.equals(status)) {
            if (cmd.earlyTrue()) {
                publishChecks.rejectIfInvalid(entity);
                requireCas(publishFreeze.freezeToPublished(entity, false));
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
            requireCas(publishFreeze.freezeToPublished(entity, true));
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
        offlineExpiry.recomputeInProgressExpiry(id, now);
        publishFreeze.evictPublishedIndex();
        return PublishResponse.done(entity.getId(), entity.getCode(), versionOf(entity), entity.getStatus());
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
        return this.scheduleFailures.scheduleFailures(page);
    }

    public int scanDue() {
        return publishScanDue.scanDue();
    }

    @Transactional
    public boolean publishDue(long id) {
        return publishScanDue.publishDue(id);
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
