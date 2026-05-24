package com.marketing.task.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MetricsService {
    private final MeterRegistry registry;

    @Getter private final Counter taskViews;
    @Getter private final Counter instancesCreated;
    @Getter private final Counter stepsCompleted;
    @Getter private final Counter rewardSuccess;
    @Getter private final Counter rewardFailure;
    @Getter private final Counter claimsSuccess;
    @Getter private final Timer   filterEvaluationTime;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.taskViews         = Counter.builder("task.views").register(registry);
        this.instancesCreated  = Counter.builder("task.instances.created").register(registry);
        this.stepsCompleted    = Counter.builder("task.steps.completed").register(registry);
        this.rewardSuccess     = Counter.builder("task.rewards.success").register(registry);
        this.rewardFailure     = Counter.builder("task.rewards.failure").register(registry);
        this.claimsSuccess     = Counter.builder("prize.claims.success").register(registry);
        this.filterEvaluationTime = Timer.builder("task.filter.evaluation").register(registry);
    }

    public void incrementTaskViews() { taskViews.increment(); }
    public void incrementInstancesCreated() { instancesCreated.increment(); }
    public void incrementStepsCompleted() { stepsCompleted.increment(); }
    public void incrementRewardSuccess() { rewardSuccess.increment(); }
    public void incrementRewardFailure() { rewardFailure.increment(); }
    public void incrementClaimsSuccess() { claimsSuccess.increment(); }

    public void recordFilterTime(long millis) {
        filterEvaluationTime.record(millis, TimeUnit.MILLISECONDS);
    }
}
