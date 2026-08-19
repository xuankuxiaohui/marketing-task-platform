package com.mkt.risk;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.risk.application.RiskAuditAppender;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Risk domain beans. Persistence scan is imported so this class can keep
 * {@code @ConditionalOnBean(DataSource)} without pairing {@code @ComponentScan}
 * (Spring Boot 4 REGISTER_BEAN).
 */
@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@Import(RiskPersistenceScan.class)
public class RiskAutoConfiguration {

    @Bean
    @ConditionalOnBean(EventPublisher.class)
    RiskAuditAppender riskAuditAppender(EventPublisher eventPublisher) {
        return new RiskAuditAppender(eventPublisher);
    }
}
