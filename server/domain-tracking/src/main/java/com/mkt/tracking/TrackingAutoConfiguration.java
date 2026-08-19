package com.mkt.tracking;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.tracking.application.EventLogStore;
import com.mkt.tracking.application.EventMetadataStore;
import com.mkt.tracking.application.TrackAuditAppender;
import com.mkt.tracking.application.TrackBatchService;
import com.mkt.tracking.domain.DisabledEventPolicy;
import com.mkt.tracking.domain.UnregisteredPolicy;
import com.mkt.tracking.support.TrackDropCounters;
import com.mkt.tracking.support.TrackSettings;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Tracking domain beans. Persistence scan is imported so this class can keep
 * {@code @ConditionalOnBean(DataSource)} without pairing {@code @ComponentScan}
 * (Spring Boot 4 REGISTER_BEAN).
 */
@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@Import(TrackingPersistenceScan.class)
public class TrackingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TrackSettings trackSettings(
            @Value("${mkt.track.batch-max-size:50}") int batchMaxSize,
            @Value("${mkt.track.event-max-payload-kb:8}") int eventMaxPayloadKb,
            @Value("${mkt.track.unregistered-policy:accept}") String unregisteredPolicy,
            @Value("${mkt.track.disabled-event-policy:drop-count}") String disabledEventPolicy,
            @Value("${mkt.track.rate-limit-per-minute:60}") int rateLimitPerMinute,
            @Value("${mkt.track.query-sample-ratio-percent:1}") int querySampleRatioPercent,
            @Value("${mkt.track.query-rate-limit-per-minute:60}") int queryRateLimitPerMinute,
            @Value("${mkt.track.retention-event-days:90}") int retentionEventDays) {
        TrackSettings settings = new TrackSettings();
        settings.setBatchMaxSize(batchMaxSize);
        settings.setEventMaxPayloadKb(eventMaxPayloadKb);
        settings.setUnregisteredPolicy(UnregisteredPolicy.fromConfig(unregisteredPolicy));
        settings.setDisabledEventPolicy(DisabledEventPolicy.fromConfig(disabledEventPolicy));
        settings.setRateLimitPerMinute(rateLimitPerMinute);
        settings.setQuerySampleRatioPercent(querySampleRatioPercent);
        settings.setQueryRateLimitPerMinute(queryRateLimitPerMinute);
        settings.setRetentionEventDays(retentionEventDays);
        return settings;
    }

    @Bean
    @ConditionalOnMissingBean
    TrackDropCounters trackDropCounters() {
        return new TrackDropCounters();
    }

    @Bean
    @ConditionalOnBean(EventPublisher.class)
    @ConditionalOnMissingBean
    TrackAuditAppender trackAuditAppender(EventPublisher eventPublisher) {
        return new TrackAuditAppender(eventPublisher);
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
