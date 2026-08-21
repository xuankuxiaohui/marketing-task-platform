package com.mkt.task.engine;

import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.expression.CrowdResolver;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;

/** enter after claim INSERT; delegates to {@link StepEngine}. */
public final class ClaimEnterEngine {

    private final StepEngine engine;

    public ClaimEnterEngine(
            TaskInstanceStore instances, EventPublisher events, Clock clock, TaskSettings settings) {
        this(instances, events, clock, settings, null);
    }

    public ClaimEnterEngine(
            TaskInstanceStore instances,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            RewardPort rewards) {
        this.engine = new StepEngine(instances, null, events, clock, settings, rewards);
    }

    public void enter(
            TaskInstanceEntity instance, SnapshotContent snapshot, UserAttributes attrs, CrowdResolver crowds) {
        enter(instance, snapshot, attrs, crowds, null, null);
    }

    public void enter(
            TaskInstanceEntity instance,
            SnapshotContent snapshot,
            UserAttributes attrs,
            CrowdResolver crowds,
            String ip,
            String deviceId) {
        engine.enter(instance, snapshot, attrs, crowds, ip, deviceId);
    }
}
