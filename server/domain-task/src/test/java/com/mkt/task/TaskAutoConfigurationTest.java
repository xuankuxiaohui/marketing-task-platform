package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.ComponentScan;

class TaskAutoConfigurationTest {

    @Test
    void classDoesNotCombineOnBeanWithComponentScan() {
        assertThat(TaskAutoConfiguration.class.getAnnotation(ConditionalOnBean.class)).isNotNull();
        assertThat(TaskAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNull();
        assertThat(TaskPersistenceScan.class.getAnnotation(ComponentScan.class)).isNotNull();
        assertThat(TaskPersistenceScan.class.getAnnotation(ConditionalOnBean.class)).isNull();
        assertThat(TaskAdminAutoConfiguration.class.getAnnotation(ConditionalOnBean.class)).isNull();
        assertThat(TaskAdminAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNotNull();
        assertThat(TaskAdminSupportAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNull();
        assertThat(TaskAdminSupportAutoConfiguration.class.getAnnotation(ConditionalOnBean.class)).isNull();
        assertThat(TaskPortalAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNotNull();
        assertThat(TaskPortalAutoConfiguration.class.getAnnotation(ConditionalOnBean.class)).isNull();
    }
}
