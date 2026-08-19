package com.mkt.portal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class PortalApplicationScanTest {

    @Test
    void portalApplicationScansOnlyItsPackage() {
        SpringBootApplication ann = PortalApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(ann).isNotNull();
        assertThat(ann.scanBasePackages()).isEmpty();
        assertThat(ann.scanBasePackageClasses()).isEmpty();
        assertThat(PortalApplication.class.getPackageName()).isEqualTo("com.mkt.portal");
    }
}
