package com.mkt.task.application;

import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserAttributePort;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.task.engine.ClaimEnterEngine;
import com.mkt.task.engine.StepEngineAdvance;
import com.mkt.task.engine.StepEngineCascadeSupport;
import com.mkt.task.engine.StepEngineExpire;
import com.mkt.task.engine.StepEngineReward;
import com.mkt.task.engine.StepEngineViews;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.TaskSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskClaimAppService {

    private final ClaimStart claimStart;

    @Autowired
    public TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            ObjectProvider<UserAttributePort> users,
            ObjectProvider<EventPublisher> events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            ClaimInsert insert,
            ClaimViews views,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps,
            ClaimStart claimStart) {
        this(
                definitions,
                instances,
                crowds,
                users.getIfAvailable(),
                events.getIfAvailable(),
                clock,
                settings,
                checks,
                insert,
                views,
                rewardOps,
                cascadeOps,
                advanceOps,
                engineViews,
                expireOps,
                claimStart);
    }

    public TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskMutexGroupStore mutexGroups,
            UserAttributePort users,
            RiskCheckPort risk,
            EventPublisher events,
            Clock clock,
            TaskSettings settings) {
        this(definitions, snapshots, instances, crowds, mutexGroups, users, risk, events, null, clock, settings);
    }

    public TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskVersionSnapshotStore snapshots,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            TaskMutexGroupStore mutexGroups,
            UserAttributePort users,
            RiskCheckPort risk,
            EventPublisher events,
            RewardPort rewards,
            Clock clock,
            TaskSettings settings) {
        this(
                definitions,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                new ClaimChecks(mutexGroups, instances, risk, settings),
                new ClaimInsert(snapshots, instances, settings),
                new ClaimViews(snapshots, instances, crowds, users),
                new StepEngineReward(instances, events, clock, rewards),
                null);
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            ClaimInsert insert,
            ClaimViews views,
            StepEngineReward rewardOps,
            ClaimStart claimStart) {
        this(
                definitions,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                checks,
                insert,
                views,
                rewardOps,
                new StepEngineCascadeSupport(instances, events, clock, settings, rewardOps),
                claimStart);
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            ClaimInsert insert,
            ClaimViews views,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            ClaimStart claimStart) {
        this(
                definitions,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                checks,
                insert,
                views,
                rewardOps,
                cascadeOps,
                new StepEngineAdvance(instances, clock, cascadeOps),
                claimStart);
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            ClaimInsert insert,
            ClaimViews views,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            ClaimStart claimStart) {
        this(
                definitions,
                instances,
                crowds,
                users,
                events,
                clock,
                settings,
                checks,
                insert,
                views,
                rewardOps,
                cascadeOps,
                advanceOps,
                new StepEngineViews(instances),
                new StepEngineExpire(),
                claimStart);
    }

    private TaskClaimAppService(
            TaskDefinitionStore definitions,
            TaskInstanceStore instances,
            TaskCrowdStore crowds,
            UserAttributePort users,
            EventPublisher events,
            Clock clock,
            TaskSettings settings,
            ClaimChecks checks,
            ClaimInsert insert,
            ClaimViews views,
            StepEngineReward rewardOps,
            StepEngineCascadeSupport cascadeOps,
            StepEngineAdvance advanceOps,
            StepEngineViews engineViews,
            StepEngineExpire expireOps,
            ClaimStart claimStart) {
        ClaimEnterEngine enter = new ClaimEnterEngine(
                instances, events, clock, settings, rewardOps, cascadeOps, advanceOps, engineViews, expireOps);
        // Manual/IT chain passes null and shares this engine. Production passes the injected collaborator.
        this.claimStart = claimStart != null
                ? claimStart
                : new ClaimStart(definitions, instances, crowds, users, events, clock, checks, insert, views, enter);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskStartResponse start(long taskId, long userId, String ip, String deviceId, String platform) {
        return claimStart.start(taskId, userId, ip, deviceId, platform);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskStartResponse start(
            long taskId, long userId, String ip, String deviceId, String platform, boolean simulated) {
        return claimStart.start(taskId, userId, ip, deviceId, platform, simulated);
    }
}
