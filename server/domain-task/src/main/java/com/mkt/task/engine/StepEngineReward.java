package com.mkt.task.engine;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RewardPort;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.task.application.TaskInstanceStore;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.convert.SnapshotContent;
import com.mkt.task.convert.TaskTime;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.domain.SkipReasons;
import com.mkt.task.domain.StepStatuses;
import com.mkt.task.entity.TaskInstanceEntity;
import com.mkt.task.entity.TaskInstanceStepEntity;
import com.mkt.task.response.RewardFeedbackView;
import com.mkt.task.support.TaskErrorCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reward helpers extracted from {@link StepEngine} (R9-1).
 * Move-only: grantReward / skipPermanentFailed / holdComplete semantics unchanged.
 * {@code stepDef}, {@code rejectIfExpired}, {@code appendStepComplete} (and the helpers they call)
 * are copied so the moved bodies stay verbatim.
 */
@Component
public class StepEngineReward {

    private final TaskInstanceStore instances;
    private final EventPublisher events;
    private final Clock clock;
    private final RewardPort rewards;

    @Autowired
    public StepEngineReward(
            TaskInstanceStore instances,
            ObjectProvider<EventPublisher> events,
            Clock clock,
            ObjectProvider<RewardPort> rewards) {
        this(instances, events.getIfAvailable(), clock, rewards.getIfAvailable());
    }

    public StepEngineReward(TaskInstanceStore instances, EventPublisher events, Clock clock, RewardPort rewards) {
        this.instances = instances;
        this.events = events;
        this.clock = clock;
        this.rewards = rewards;
    }

    boolean grantReward(
            TaskInstanceEntity instance,
            TaskInstanceStepEntity step,
            SnapshotContent snapshot,
            boolean sameRequestInsert,
            Instant now,
            List<RewardFeedbackView> feedback,
            String ip,
            String deviceId) {
        if (rewards == null) {
            return false;
        }
        TaskStepCommand def = stepDef(snapshot, step.getStepCode());
        if (def == null || def.prizeId() == null) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
        Long elapsed = null;
        if (!sameRequestInsert) {
            Instant started = TaskTime.toInstant(instance.getStartedAt());
            if (started != null) {
                elapsed = Math.max(0L, Duration.between(started, now).toSeconds());
            }
        }
        boolean simulated = instance.getSimulated() != null && instance.getSimulated() == 1;
        GrantContext ctx = new GrantContext(null, List.of(), null, simulated, elapsed, ip, deviceId);
        try {
            GrantResult result = rewards.grant(
                    def.prizeId(),
                    instance.getUserId(),
                    GrantSource.TASK_STEP,
                    String.valueOf(step.getId()),
                    ctx);
            if (result != null
                    && (result.status() == GrantStatus.GRANTED || result.status() == GrantStatus.WON)) {
                if (!holdComplete(instance, step)) {
                    return false;
                }
                String name = result.prizeName();
                if (name == null || name.isBlank()) {
                    name = def.name() == null ? step.getStepCode() : def.name();
                }
                feedback.add(new RewardFeedbackView(name, 1));
                return true;
            }
            if (result != null && result.status() == GrantStatus.PERMANENT_FAILED) {
                return skipPermanentFailed(instance, step, clock.instant());
            }
            return false;
        } catch (RetryableGrantException ex) {
            return false;
        } catch (PermanentGrantException ex) {
            return skipPermanentFailed(instance, step, clock.instant());
        }
    }

    private boolean skipPermanentFailed(TaskInstanceEntity instance, TaskInstanceStepEntity step, Instant now) {
        rejectIfExpired(instance, now);
        int version = step.getVersion() == null ? 0 : step.getVersion();
        int affected = instances.skipStepCas(
                step.getId(), version, TaskTime.toUtc(now), SkipReasons.GRANT_PERMANENT_FAILED);
        if (affected == 1) {
            step.setStatus(StepStatuses.SKIPPED);
            step.setSkipReason(SkipReasons.GRANT_PERMANENT_FAILED);
            step.setCompletedAt(TaskTime.toUtc(now));
            step.setVersion(version + 1);
            return true;
        }
        return false;
    }

    boolean holdComplete(TaskInstanceEntity instance, TaskInstanceStepEntity step) {
        Instant now = clock.instant();
        rejectIfExpired(instance, now);
        int version = step.getVersion() == null ? 0 : step.getVersion();
        int affected = instances.completeStepCas(step.getId(), version, TaskTime.toUtc(now), null);
        if (affected == 1) {
            step.setStatus(StepStatuses.COMPLETED);
            step.setCompletedAt(TaskTime.toUtc(now));
            step.setVersion(version + 1);
            appendStepComplete(instance, step);
            return true;
        }
        return false;
    }

    private void appendStepComplete(TaskInstanceEntity instance, TaskInstanceStepEntity step) {
        if (events == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("instanceId", instance.getId());
        payload.put("taskId", instance.getTaskId());
        payload.put("stepCode", step.getStepCode());
        payload.put("seq", seqOf(step));
        payload.put("userId", instance.getUserId());
        payload.put("simulated", simulated(instance));
        events.append(EventCodes.TASK_STEP_COMPLETE, "task_instance", String.valueOf(instance.getId()), payload);
    }

    private static boolean simulated(TaskInstanceEntity instance) {
        return instance.getSimulated() != null && instance.getSimulated() == 1;
    }

    private static void rejectIfExpired(TaskInstanceEntity instance, Instant now) {
        if (expired(instance, now)) {
            throw new BusinessException(TaskErrorCodes.INSTANCE_EXPIRED);
        }
    }

    private static boolean expired(TaskInstanceEntity instance, Instant now) {
        if (InstanceStatuses.EXPIRED.equals(instance.getStatus())) {
            return true;
        }
        if (!InstanceStatuses.IN_PROGRESS.equals(instance.getStatus())) {
            return false;
        }
        Instant expireAt = TaskTime.toInstant(instance.getExpireAt());
        return expireAt != null && !now.isBefore(expireAt);
    }

    private static int seqOf(TaskInstanceStepEntity step) {
        return step.getSeq() == null ? 0 : step.getSeq();
    }

    private static TaskStepCommand stepDef(SnapshotContent snapshot, String stepCode) {
        if (snapshot == null || snapshot.steps() == null || stepCode == null) {
            return null;
        }
        for (TaskStepCommand step : snapshot.steps()) {
            if (stepCode.equals(step.code())) {
                return step;
            }
        }
        return null;
    }
}
