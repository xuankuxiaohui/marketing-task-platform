package com.mkt.task.application;

import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.domain.DefinitionStatuses;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.response.BatchItemResponse;
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
    private final PublishExecute publishExecute;
    private final PublishSchedule publishSchedule;
    private final PublishResetRevision publishResetRevision;

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
            PublishScanDue publishScanDue,
            PublishExecute publishExecute,
            PublishSchedule publishSchedule,
            PublishResetRevision publishResetRevision) {
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
        this.publishExecute = publishExecute;
        this.publishSchedule = publishSchedule;
        this.publishResetRevision = publishResetRevision;
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
        this.publishExecute = new PublishExecute(definitions, this.publishChecks, this.publishFreeze, clock);
        this.publishSchedule = new PublishSchedule(definitions, this.publishChecks, clock);
        this.publishResetRevision = new PublishResetRevision(definitions, mutexGroups, snapshots, definitionsApp);
    }

    @Transactional
    public PublishResponse publish(long id, PublishCommand command) {
        return publishExecute.publish(id, command);
    }

    @Transactional
    public PublishResponse schedule(long id, ScheduleCommand command) {
        return publishSchedule.schedule(id, command);
    }

    @Transactional
    public PublishResponse cancelSchedule(long id) {
        return publishSchedule.cancelSchedule(id);
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
        return publishResetRevision.resetRevision(id);
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

    private static int versionOf(TaskDefinitionEntity entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    @FunctionalInterface
    private interface IdAction {
        void run(long id);
    }
}
