package com.mkt.task.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.AbandonSources;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Instance abandon helpers extracted from {@link TaskInstanceAppService} (R12-3).
 * Move-only: abandon / append semantics unchanged.
 */
@Component
public class InstanceAbandonOps {

    private final TaskInstanceStore instances;
    private final EventPublisher publisher;
    private final Clock clock;

    @Autowired
    public InstanceAbandonOps(
            TaskInstanceStore instances, ObjectProvider<EventPublisher> publisher, Clock clock) {
        this(instances, publisher.getIfAvailable(), clock);
    }

    public InstanceAbandonOps(TaskInstanceStore instances, EventPublisher publisher, Clock clock) {
        this.instances = instances;
        this.publisher = publisher;
        this.clock = clock;
    }

    InstanceAbandonResponse abandon(TaskInstanceEntity row, String source) {
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

    void append(String eventCode, TaskInstanceEntity row, Map<String, Object> payload) {
        if (publisher == null) {
            return;
        }
        publisher.append(eventCode, "task_instance", String.valueOf(row.getId()), payload);
    }

    private static int costSeconds(TaskInstanceEntity row, Instant now) {
        Instant started = TaskTime.toInstant(row.getStartedAt());
        if (started == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(started, now).toSeconds());
    }
}
