package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.ComponentScan;

class RiskAutoConfigurationTest {

    @Test
    void classDoesNotCombineOnBeanWithComponentScan() {
        assertThat(RiskAutoConfiguration.class.getAnnotation(ConditionalOnBean.class)).isNotNull();
        assertThat(RiskAutoConfiguration.class.getAnnotation(ComponentScan.class)).isNull();
        assertThat(RiskPersistenceScan.class.getAnnotation(ComponentScan.class)).isNotNull();
        assertThat(RiskPersistenceScan.class.getAnnotation(ConditionalOnBean.class)).isNull();
    }
}
