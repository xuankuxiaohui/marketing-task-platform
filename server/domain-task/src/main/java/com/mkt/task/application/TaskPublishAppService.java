package com.mkt.task.application;

import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.response.BatchItemResponse;
import com.mkt.task.response.PublishResponse;
import com.mkt.task.response.ScheduleFailureView;
import com.mkt.task.response.TaskVersionView;
import com.mkt.task.response.VersionDiffResponse;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskPublishAppService {

    private final TaskDefinitionStore definitions;
    private final TaskMutexGroupStore mutexGroups;
    private final TaskVersionSnapshotStore snapshots;
    private final TaskDefinitionAppService definitionsApp;
    private final PrizeEnabledLookup prizes;
    private final TaskPublishChecks publishChecks;
    private final TaskPublishFreeze publishFreeze;
    private final TaskPublishScheduleFailures scheduleFailures;
    private final PublishScanDue publishScanDue;
    private final PublishExecute publishExecute;
    private final PublishSchedule publishSchedule;
    private final PublishResetRevision publishResetRevision;
    private final PublishOffline publishOffline;
    private final PublishBatch publishBatch;
    private final PublishVersionReads publishVersionReads;

    @Autowired
    public TaskPublishAppService(
            TaskDefinitionStore definitions,
            TaskMutexGroupStore mutexGroups,
            TaskVersionSnapshotStore snapshots,
            TaskDefinitionAppService definitionsApp,
            PrizeEnabledLookup prizes,
            TaskPublishChecks publishChecks,
            TaskPublishFreeze publishFreeze,
            TaskPublishScheduleFailures scheduleFailures,
            PublishScanDue publishScanDue,
            PublishExecute publishExecute,
            PublishSchedule publishSchedule,
            PublishResetRevision publishResetRevision,
            PublishOffline publishOffline,
            PublishBatch publishBatch,
            PublishVersionReads publishVersionReads) {
        this.definitions = definitions;
        this.mutexGroups = mutexGroups;
        this.snapshots = snapshots;
        this.definitionsApp = definitionsApp;
        this.prizes = prizes;
        this.publishChecks = publishChecks;
        this.publishFreeze = publishFreeze;
        this.scheduleFailures = scheduleFailures;
        this.publishScanDue = publishScanDue;
        this.publishExecute = publishExecute;
        this.publishSchedule = publishSchedule;
        this.publishResetRevision = publishResetRevision;
        this.publishOffline = publishOffline;
        this.publishBatch = publishBatch;
        this.publishVersionReads = publishVersionReads;
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
        this.publishChecks = new TaskPublishChecks(definitions, definitionsApp, prizes);
        this.publishFreeze = new TaskPublishFreeze(
                definitions, mutexGroups, snapshots, definitionsApp, clock, cache);
        this.scheduleFailures = new TaskPublishScheduleFailures(audits, alerts, jdbc, tx);
        PublishOfflineExpiry offlineExpiry = new PublishOfflineExpiry(instances, snapshots, settings);
        this.publishScanDue = new PublishScanDue(
                definitions, this.publishChecks, this.publishFreeze, this.scheduleFailures, clock, tx);
        this.publishExecute = new PublishExecute(definitions, this.publishChecks, this.publishFreeze, clock);
        this.publishSchedule = new PublishSchedule(definitions, this.publishChecks, clock);
        this.publishResetRevision = new PublishResetRevision(definitions, mutexGroups, snapshots, definitionsApp);
        this.publishOffline = new PublishOffline(definitions, offlineExpiry, this.publishFreeze, clock);
        this.publishBatch = new PublishBatch(this.publishExecute, this.publishOffline, tx);
        this.publishVersionReads = new PublishVersionReads(definitions, new TaskPublishVersionDiff(snapshots));
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
        return publishOffline.offline(id);
    }

    @Transactional
    public PublishResponse resetRevision(long id) {
        return publishResetRevision.resetRevision(id);
    }

    public List<BatchItemResponse> batchPublish(List<Long> ids) {
        return publishBatch.batchPublish(ids);
    }

    public List<BatchItemResponse> batchOffline(List<Long> ids) {
        return publishBatch.batchOffline(ids);
    }

    public List<TaskVersionView> versions(long id) {
        return publishVersionReads.versions(id);
    }

    public VersionDiffResponse diff(long id, Integer left, Integer right) {
        return publishVersionReads.diff(id, left, right);
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
}
