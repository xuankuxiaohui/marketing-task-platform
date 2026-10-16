package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.PageData;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.query.InstanceQuery;
import com.mkt.task.response.AdminInstanceDetailResponse;
import com.mkt.task.response.AdminInstanceView;
import com.mkt.task.response.InstanceAbandonResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
    private final InstanceExpireOps expireOps;
    private final Clock clock;
    private final InstancePage instancePage;
    private final InstanceGet instanceGet;
    private final InstanceAbandon instanceAbandon;

    @Autowired
    public TaskInstanceAppService(
            TaskInstanceStore instances,
            ObjectProvider<InstanceEventStore> events,
            Clock clock,
            InstanceViews views,
            InstanceGuards guards,
            InstanceAbandonOps abandonOps,
            InstanceExpireOps expireOps,
            InstancePage instancePage,
            InstanceGet instanceGet,
            InstanceAbandon instanceAbandon) {
        this(
                instances,
                views,
                events.getIfAvailable(),
                guards,
                abandonOps,
                expireOps,
                clock,
                instancePage,
                instanceGet,
                instanceAbandon);
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
                null,
                null,
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
            InstancePage instancePage,
            InstanceGet instanceGet,
            InstanceAbandon instanceAbandon) {
        this.instances = instances;
        this.expireOps = expireOps;
        this.clock = clock;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.instancePage =
                instancePage != null
                        ? instancePage
                        : new InstancePage(instances, views);
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.instanceGet =
                instanceGet != null
                        ? instanceGet
                        : new InstanceGet(instances, views, events);
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.instanceAbandon =
                instanceAbandon != null
                        ? instanceAbandon
                        : new InstanceAbandon(instances, guards, abandonOps, clock);
    }

    public PageData<AdminInstanceView> page(InstanceQuery query) {
        return instancePage.page(query);
    }

    public AdminInstanceDetailResponse get(long id) {
        return instanceGet.get(id);
    }

    @Transactional
    public InstanceAbandonResponse abandonAdmin(long id) {
        return instanceAbandon.abandonAdmin(id);
    }

    @Transactional
    public InstanceAbandonResponse abandonUser(long instanceId, long userId, String ip, String deviceId) {
        return instanceAbandon.abandonUser(instanceId, userId, ip, deviceId);
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
