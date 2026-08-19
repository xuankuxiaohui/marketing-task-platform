package com.mkt.tracking;

import com.mkt.infra.lock.PlatformLock;
import com.mkt.tracking.schedule.EvtPartitionScheduler;
import com.mkt.tracking.support.TrackSettings;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/** Partition scheduler 8 only on admin-app (design §6.7). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
public class TrackingAdminAutoConfiguration {

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformLock.class, TrackSettings.class})
    @ConditionalOnMissingBean
    EvtPartitionScheduler evtPartitionScheduler(
            JdbcTemplate jdbcTemplate, PlatformLock platformLock, TrackSettings trackSettings, Clock clock) {
        return new EvtPartitionScheduler(jdbcTemplate, platformLock, trackSettings, clock);
    }
}
