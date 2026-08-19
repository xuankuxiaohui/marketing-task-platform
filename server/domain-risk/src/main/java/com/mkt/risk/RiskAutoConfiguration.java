package com.mkt.risk;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.risk.application.RiskAuditAppender;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Risk domain beans. Class-level {@code @ConditionalOnBean} cannot pair with {@code @ComponentScan}
 * (Spring Boot 4 REGISTER_BEAN). Optional beans stay gated on {@code @Bean} methods.
 */
@AutoConfiguration
@MapperScan("com.mkt.risk.mapper")
@ComponentScan(basePackages = {"com.mkt.risk.application", "com.mkt.risk.support"})
public class RiskAutoConfiguration {

    @Bean
    @ConditionalOnBean(EventPublisher.class)
    RiskAuditAppender riskAuditAppender(EventPublisher eventPublisher) {
        return new RiskAuditAppender(eventPublisher);
    }
}
