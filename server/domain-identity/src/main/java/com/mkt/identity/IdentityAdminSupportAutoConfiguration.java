package com.mkt.identity;

import com.mkt.identity.application.IdentityAuditAppender;
import com.mkt.identity.audit.AuditedAspect;
import com.mkt.identity.config.ConfigService;
import com.mkt.identity.schedule.AuditCleanScheduler;
import com.mkt.infra.lock.PlatformLock;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Beans that use REGISTER_BEAN conditions — must not sit on a @ComponentScan class (Boot 4). */
@AutoConfiguration
@ConditionalOnClass(name = "com.mkt.admin.AdminApplication")
public class IdentityAdminSupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditedAspect auditedAspect(IdentityAuditAppender audits, PlatformTransactionManager transactionManager) {
        return new AuditedAspect(audits, transactionManager);
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, PlatformLock.class, ConfigService.class})
    @ConditionalOnMissingBean
    AuditCleanScheduler auditCleanScheduler(
            JdbcTemplate jdbcTemplate, PlatformLock platformLock, ConfigService configService, Clock clock) {
        return new AuditCleanScheduler(jdbcTemplate, platformLock, configService, clock);
    }
}
