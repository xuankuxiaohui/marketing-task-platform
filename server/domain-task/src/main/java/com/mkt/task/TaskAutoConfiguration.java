package com.mkt.task;

import com.mkt.task.support.TaskSettings;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Task domain beans. Persistence scan is imported so this class can keep
 * {@code @ConditionalOnBean(DataSource)} without pairing {@code @ComponentScan}.
 */
@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@Import(TaskPersistenceScan.class)
public class TaskAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TaskSettings taskSettings() {
        return new TaskSettings();
    }
}
