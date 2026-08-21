package com.mkt.admin.metrics;

import com.mkt.identity.config.ConfigService;
import com.mkt.infra.lock.PlatformLock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class MetricsConfiguration {

    @Bean
    MetricsAggregateService metricsAggregateService(JdbcTemplate jdbc, ConfigService configs, Clock clock) {
        return new MetricsAggregateService(jdbc, configs, clock);
    }

    @Bean
    MetricsQueryService metricsQueryService(JdbcTemplate jdbc, Clock clock) {
        return new MetricsQueryService(jdbc, clock);
    }

    @Bean
    MetricsScheduler metricsScheduler(MetricsAggregateService aggregates, PlatformLock locks, Clock clock) {
        return new MetricsScheduler(aggregates, locks, clock);
    }
}
