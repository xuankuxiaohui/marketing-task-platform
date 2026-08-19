package com.mkt.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.ComponentScan;

class TrackingAutoConfigurationTest {

    @Test
    void classDoesNotCombineOnBeanWithComponentScan() {
        assertThat(TrackingAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNotNull();
        assertThat(TrackingAutoConfiguration.class.getAnnotation(ConditionalOnBean.class)).isNull();
    }
}
