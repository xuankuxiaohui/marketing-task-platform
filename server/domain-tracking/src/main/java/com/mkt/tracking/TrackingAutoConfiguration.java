package com.mkt.tracking;

import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.tracking.application.EventLogStore;
import com.mkt.tracking.application.EventMetadataStore;
import com.mkt.tracking.application.TrackBatchService;
import com.mkt.tracking.domain.DisabledEventPolicy;
import com.mkt.tracking.domain.UnregisteredPolicy;
import com.mkt.tracking.support.TrackDropCounters;
import com.mkt.tracking.support.TrackSettings;
import java.time.Clock;
import javax.sql.DataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@MapperScan("com.mkt.tracking.mapper")
@ComponentScan(basePackages = "com.mkt.tracking.application")
public class TrackingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TrackSettings trackSettings(
            @Value("${mkt.track.batch-max-size:50}") int batchMaxSize,
            @Value("${mkt.track.event-max-payload-kb:8}") int eventMaxPayloadKb,
            @Value("${mkt.track.unregistered-policy:accept}") String unregisteredPolicy,
            @Value("${mkt.track.disabled-event-policy:drop-count}") String disabledEventPolicy,
            @Value("${mkt.track.rate-limit-per-minute:60}") int rateLimitPerMinute,
            @Value("${mkt.track.retention-event-days:90}") int retentionEventDays) {
        TrackSettings settings = new TrackSettings();
        settings.setBatchMaxSize(batchMaxSize);
        settings.setEventMaxPayloadKb(eventMaxPayloadKb);
        settings.setUnregisteredPolicy(UnregisteredPolicy.fromConfig(unregisteredPolicy));
        settings.setDisabledEventPolicy(DisabledEventPolicy.fromConfig(disabledEventPolicy));
        settings.setRateLimitPerMinute(rateLimitPerMinute);
        settings.setRetentionEventDays(retentionEventDays);
        return settings;
    }

    @Bean
    @ConditionalOnMissingBean
    TrackDropCounters trackDropCounters() {
        return new TrackDropCounters();
    }

    @Bean
    @ConditionalOnMissingBean
    TrackBatchService trackBatchService(
            EventLogStore eventLogStore,
            EventMetadataStore metadataStore,
            SlidingWindowRateLimiter rateLimiter,
            TrackSettings settings,
            TrackDropCounters drops,
            Clock clock) {
        return new TrackBatchService(eventLogStore, metadataStore, rateLimiter, settings, drops, clock);
    }
}
