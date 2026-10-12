package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mkt.identity.config.ConfigService;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.MemoryKeyValueStore;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetricsConfigurationTest {

    @Test
    void wiresAggregateQueryAndScheduler() {
        MetricsConfiguration config = new MetricsConfiguration();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConfigService configs = (key, def) -> def;
        Clock clock = Clock.systemUTC();
        MetricsAggregateService aggregates = config.metricsAggregateService(jdbc, configs, clock);
        MetricsQueryService queries = config.metricsQueryService(jdbc, clock);
        MetricsScheduler scheduler =
                config.metricsScheduler(aggregates, new PlatformLock(new MemoryKeyValueStore()), clock, true);
        assertThat(aggregates).isNotNull();
        assertThat(queries).isNotNull();
        assertThat(scheduler).isNotNull();
    }

    @Test
    void wiresSchedulerDisabledShortCircuit() {
        MetricsConfiguration config = new MetricsConfiguration();
        MetricsAggregateService aggregates = mock(MetricsAggregateService.class);
        PlatformLock locks = mock(PlatformLock.class);
        MetricsScheduler scheduler =
                config.metricsScheduler(aggregates, locks, Clock.systemUTC(), false);
        assertThat(scheduler).isNotNull();
        scheduler.tick();
        verifyNoInteractions(aggregates, locks);
    }
}
