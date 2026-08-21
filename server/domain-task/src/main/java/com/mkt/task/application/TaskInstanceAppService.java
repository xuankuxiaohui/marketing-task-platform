package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskListType;
import com.mkt.contract.UserRiskSummary;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.SnapshotViews;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.AbandonSources;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.engine.StepEngine;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.entity.TaskVersionSnapshotEntity;
import com.mkt.task.query.InstanceQuery;
import com.mkt.task.response.AdminInstanceDetailResponse;
import com.mkt.task.response.AdminInstanceStepView;
import com.mkt.task.response.AdminInstanceView;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.response.InstanceEventView;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskInstanceAppService {

    static final int EXPIRE_BATCH = 100;

    private final TaskInstanceStore instances;
    private final TaskVersionSnapshotStore snapshots;
    private final InstanceEventStore events;
    private final EventPublisher publisher;
    private final RiskCheckPort risk;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Autowired
    public TaskInstanceAppService(
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            ObjectProvider<InstanceEventStore> events,
            ObjectProvider<EventPublisher> publisher,
            ObjectProvider<RiskCheckPort> risk,
            Clock clock,
            ObjectProvider<TransactionTemplate> tx) {
        this(
                instances,
                snapshots,
                events.getIfAvailable(),
                publisher.getIfAvailable(),
                risk.getIfAvailable(),
                clock,
                tx.getIfAvailable());
    }

    public TaskInstanceAppService(
            TaskInstanceStore instances,
            TaskVersionSnapshotStore snapshots,
            InstanceEventStore events,
            EventPublisher publisher,
            RiskCheckPort risk,
            Clock clock,
            TransactionTemplate tx) {
        this.instances = instances;
        this.snapshots = snapshots;
        this.events = events;
        this.publisher = publisher;
        this.risk = risk;
        this.clock = clock;
        this.tx = tx;
    }

    public PageData<AdminInstanceView> page(InstanceQuery query) {
        LocalDateTime from = TaskTime.toUtc(query.from());
        LocalDateTime to = TaskTime.toUtc(query.to());
        String status = blankToNull(query.status());
        long total = instances.countAdmin(query.taskId(), query.userId(), status, query.simulated(), from, to);
        List<TaskInstanceEntity> rows = instances.listAdmin(
                query.taskId(),
                query.userId(),
                status,
                query.simulated(),
                from,
                to,
                query.page().offset(),
                query.page().pageSize());
        List<AdminInstanceView> views = new ArrayList<>(rows.size());
        for (TaskInstanceEntity row : rows) {
            views.add(toView(row));
        }
        return new PageData<>(total, views);
    }

    public AdminInstanceDetailResponse get(long id) {
        TaskInstanceEntity row = instances.getById(id);
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        SnapshotContent snapshot = snapshotOf(row);
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
        return new AdminInstanceDetailResponse(toView(row), steps, timeline);
    }

    @Transactional
    public InstanceAbandonResponse abandonAdmin(long id) {
        TaskInstanceEntity row = instances.getById(id);
        if (row == null) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            return new InstanceAbandonResponse(row.getStatus());
        }
        return abandon(row, AbandonSources.ADMIN);
    }

    @Transactional
    public InstanceAbandonResponse abandonUser(long instanceId, long userId, String ip, String deviceId) {
        TaskInstanceEntity row = instances.getById(instanceId);
        if (row == null || row.getUserId() == null || row.getUserId() != userId) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
        }
        rejectIfFrozen(userId);
        if (StepEngine.expired(row, clock.instant())) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
        if (InstanceStatuses.ABANDONED.equals(row.getStatus())) {
            return new InstanceAbandonResponse(InstanceStatuses.ABANDONED);
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        return abandon(row, AbandonSources.USER);
    }

    public int expireDue() {
        Instant now = clock.instant();
        LocalDateTime nowUtc = TaskTime.toUtc(now);
        int total = 0;
        List<TaskInstanceEntity> due;
        do {
            due = instances.listDueToExpire(nowUtc, EXPIRE_BATCH);
            for (TaskInstanceEntity row : due) {
                boolean flipped = runInTx(() -> expireOne(row.getId(), now));
                if (flipped) {
                    total++;
                }
            }
        } while (due.size() == EXPIRE_BATCH);
        return total;
    }

    boolean expireOne(long id, Instant now) {
        TaskInstanceEntity row = instances.getById(id);
        if (row == null || !InstanceStatuses.IN_PROGRESS.equals(row.getStatus())) {
            return false;
        }
        if (row.getExpireAt() == null) {
            return false;
        }
        Instant expireAt = TaskTime.toInstant(row.getExpireAt());
        if (expireAt == null || now.isBefore(expireAt)) {
            return false;
        }
        int cost = costSeconds(row, now);
        int affected = instances.expireCas(id, TaskTime.toUtc(now), cost);
        if (affected != 1) {
            return false;
        }
        append(EventCodes.TASK_INSTANCE_EXPIRE, row, Map.of(
                "instanceId", row.getId(),
                "taskId", row.getTaskId(),
                "userId", row.getUserId(),
                "costSeconds", cost));
        return true;
    }

    private InstanceAbandonResponse abandon(TaskInstanceEntity row, String source) {
        Instant now = clock.instant();
        int cost = costSeconds(row, now);
        int affected = instances.abandonCas(row.getId(), source, TaskTime.toUtc(now), cost);
        if (affected != 1) {
            TaskInstanceEntity fresh = instances.getById(row.getId());
            if (fresh == null) {
                throw new BusinessException(TaskErrorCodes.INSTANCE_NOT_FOUND);
            }
            if (AbandonSources.ADMIN.equals(source) || InstanceStatuses.ABANDONED.equals(fresh.getStatus())) {
                return new InstanceAbandonResponse(fresh.getStatus());
            }
            throw new BusinessException(TaskErrorCodes.STEP_STATE_MISMATCH);
        }
        append(EventCodes.TASK_INSTANCE_ABANDON, row, Map.of(
                "instanceId", row.getId(),
                "taskId", row.getTaskId(),
                "userId", row.getUserId(),
                "abandonSource", source));
        return new InstanceAbandonResponse(InstanceStatuses.ABANDONED);
    }

    private void append(String eventCode, TaskInstanceEntity row, Map<String, Object> payload) {
        if (publisher == null) {
            return;
        }
        publisher.append(eventCode, "task_instance", String.valueOf(row.getId()), payload);
    }

    private void rejectIfFrozen(long userId) {
        if (blacklisted(userId)) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_FROZEN);
        }
    }

    private boolean blacklisted(long userId) {
        if (risk == null) {
            return false;
        }
        UserRiskSummary summary = risk.userSummary(userId);
        return summary != null && summary.listStatus().contains(RiskListType.BLACK);
    }

    private SnapshotContent snapshotOf(TaskInstanceEntity row) {
        if (row.getSnapshotId() == null || snapshots == null) {
            return null;
        }
        TaskVersionSnapshotEntity snap = snapshots.getById(row.getSnapshotId());
        if (snap == null || snap.getContent() == null) {
            return null;
        }
        return JsonUtil.fromJson(snap.getContent(), SnapshotContent.class);
    }

    private static AdminInstanceView toView(TaskInstanceEntity row) {
        return new AdminInstanceView(
                row.getId(),
                row.getTaskId(),
                row.getTaskCode(),
                row.getUserId(),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getCycleKey(),
                row.getStatus(),
                row.getAbandonSource(),
                TaskTime.toInstant(row.getAbandonedAt()),
                TaskTime.toInstant(row.getExpireAt()),
                TaskTime.toInstant(row.getStartedAt()),
                TaskTime.toInstant(row.getCompletedAt()),
                row.getCostSeconds(),
                row.getSimulated() == null ? 0 : row.getSimulated());
    }

    private static int costSeconds(TaskInstanceEntity row, Instant now) {
        Instant started = TaskTime.toInstant(row.getStartedAt());
        if (started == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(started, now).toSeconds());
    }

    private boolean runInTx(java.util.function.BooleanSupplier action) {
        if (tx != null) {
            Boolean result = tx.execute(status -> action.getAsBoolean());
            return Boolean.TRUE.equals(result);
        }
        return action.getAsBoolean();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
