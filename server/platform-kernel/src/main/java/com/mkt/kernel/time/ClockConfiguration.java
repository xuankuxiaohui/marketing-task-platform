package com.mkt.kernel.time;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Production clock is UTC (design D-03 / §2.9). Tests replace it with {@code MutableClock}. */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }
}
