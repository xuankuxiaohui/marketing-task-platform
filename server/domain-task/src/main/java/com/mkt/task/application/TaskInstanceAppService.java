package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.AbandonSources;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.engine.StepEngineExpire;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.query.InstanceQuery;
import com.mkt.task.response.AdminInstanceDetailResponse;
import com.mkt.task.response.AdminInstanceStepView;
import com.mkt.task.response.AdminInstanceView;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.response.InstanceEventView;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskInstanceAppService {

    static final int EXPIRE_BATCH = 100;

    private final TaskInstanceStore instances;
    private final InstanceViews views;
    private final InstanceEventStore events;
    private final InstanceGuards guards;
    private final InstanceAbandonOps abandonOps;
    private final InstanceExpireOps expireOps;
    private final Clock clock;
    private final InstancePage instancePage;

    @Autowired
    public TaskInstanceAppService(
            TaskInstanceStore instances,
            ObjectProvider<InstanceEventStore> events,
            Clock clock,
            InstanceViews views,
            InstanceGuards guards,
            InstanceAbandonOps abandonOps,
            InstanceExpireOps expireOps,
            InstancePage instancePage) {
        this(
                instances,
                views,
                events.getIfAvailable(),
                guards,
                abandonOps,
                expireOps,
                clock,
                instancePage);
    }

    public TaskInstanceAppService(
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            InstanceEventStore events,
            EventPublisher publisher,
            RiskCheckPort risk,
            Clock clock,
            TransactionTemplate tx) {
        InstanceAbandonOps abandonOps = new InstanceAbandonOps(instances, publisher, clock);
        this(
                instances,
                new InstanceViews(snapshots),
                events,
                new InstanceGuards(risk),
                abandonOps,
                new InstanceExpireOps(instances, abandonOps, tx),
                clock,
                null);
    }

    private TaskInstanceAppService(
            TaskInstanceStore instances,
            InstanceViews views,
            InstanceEventStore events,
            InstanceGuards guards,
            InstanceAbandonOps abandonOps,
            InstanceExpireOps expireOps,
            Clock clock,
            InstancePage instancePage) {
        this.instances = instances;
        this.views = views;
        this.events = events;
        this.guards = guards;
        this.abandonOps = abandonOps;
        this.expireOps = expireOps;
        this.clock = clock;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.instancePage =
                instancePage != null
                        ? instancePage
                        : new InstancePage(instances, views);
    }

    public PageData<AdminInstanceView> page(InstanceQuery query) {
        return instancePage.page(query);
    }

    public AdminInstanceDetailResponse get(long id) {
        TaskInstanceEntity row = instances.getById(id);
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        SnapshotContent snapshot = views.snapshotOf(row);
        List<AdminInstanceStepView> steps = new ArrayList<>();
        for (TaskInstanceStepEntity step : instances.listSteps(id)) {
            TaskStepCommand def = snapshot == null ? null : SnapshotViews.step(snapshot, step.getStepCode());
            Integer target = def == null ? null : def.progressTarget();
            steps.add(new AdminInstanceStepView(
                    step.getStepCode(),
                    step.getType(),
                    step.getStatus(),
                    step.getProgressCurrent() == null ? 0 : step.getProgressCurrent(),
                    target,
                    TaskTime.toInstant(step.getActivatedAt()),
                    TaskTime.toInstant(step.getCompletedAt())));
        }
        List<InstanceEventView> timeline =
                events == null ? List.of() : events.listByInstance(id, row.getUserId());
        return new AdminInstanceDetailResponse(views.toView(row), steps, timeline);
    }

    @Transactional
    public InstanceAbandonResponse abandonAdmin(long id) {
        TaskInstanceEntity row = instances.getByIdForUpdate(id);
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            return new InstanceAbandonResponse(row.getStatus());
        }
        return abandonOps.abandon(row, AbandonSources.ADMIN);
    }

    @Transactional
    public InstanceAbandonResponse abandonUser(long instanceId, long userId, String ip, String deviceId) {
        TaskInstanceEntity row = instances.getByIdForUpdate(instanceId);
        if (row == null || row.getUserId() == null || row.getUserId() != userId) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        guards.rejectIfFrozen(userId);
        if (StepEngineExpire.expired(row, clock.instant())) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
        if (InstanceStatuses.ABANDONED.equals(row.getStatus())) {
            return new InstanceAbandonResponse(InstanceStatuses.ABANDONED);
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        return abandonOps.abandon(row, AbandonSources.USER);
    }

    public int expireDue() {
        Instant now = clock.instant();
        LocalDateTime nowUtc = TaskTime.toUtc(now);
        int total = 0;
        List<TaskInstanceEntity> due;
        do {
            due = instances.listDueToExpire(nowUtc, EXPIRE_BATCH);
            for (TaskInstanceEntity row : due) {
                boolean flipped = expireOps.runInTx(() -> expireOps.expireOne(row.getId(), now));
                if (flipped) {
                    total++;
                }
            }
        } while (due.size() == EXPIRE_BATCH);
        return total;
    }
}
