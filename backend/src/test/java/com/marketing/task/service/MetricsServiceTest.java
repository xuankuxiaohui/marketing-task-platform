package com.marketing.task.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsServiceTest {

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService(new SimpleMeterRegistry());
    }

    @Test
    void shouldIncrementTaskViews() {
        metricsService.incrementTaskViews();
        assertEquals(1.0, metricsService.getTaskViews().count());
    }

    @Test
    void shouldIncrementAllCounters() {
        metricsService.incrementTaskViews();
        metricsService.incrementInstancesCreated();
        metricsService.incrementStepsCompleted();
        metricsService.incrementRewardSuccess();
        metricsService.incrementRewardFailure();
        metricsService.incrementClaimsSuccess();

        assertEquals(1.0, metricsService.getTaskViews().count());
        assertEquals(1.0, metricsService.getInstancesCreated().count());
        assertEquals(1.0, metricsService.getStepsCompleted().count());
        assertEquals(1.0, metricsService.getRewardSuccess().count());
        assertEquals(1.0, metricsService.getRewardFailure().count());
        assertEquals(1.0, metricsService.getClaimsSuccess().count());
    }

    @Test
    void shouldRecordFilterTime() {
        metricsService.recordFilterTime(50);
        assertTrue(metricsService.getFilterEvaluationTime().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }
}
