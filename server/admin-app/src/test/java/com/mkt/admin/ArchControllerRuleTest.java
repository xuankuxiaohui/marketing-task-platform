package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.admin.arch.ProductionClasses;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** RL-04: controllers live in controller.admin|portal|internal (design §7.6). */
class ArchControllerRuleTest {

    @Test
    void controllersResideInFacePackages() {
        classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .or()
                .areAnnotatedWith("org.springframework.stereotype.Controller")
                .should()
                .resideInAnyPackage("..controller.admin..", "..controller.portal..", "..controller.internal..")
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }

    @Test
    void adminApplicationScansOnlyItsPackage() {
        SpringBootApplication ann = AdminApplication.class.getAnnotation(SpringBootApplication.class);
        assertThat(ann).isNotNull();
        assertThat(ann.scanBasePackages()).isEmpty();
        assertThat(ann.scanBasePackageClasses()).isEmpty();
        assertThat(AdminApplication.class.getPackageName()).isEqualTo("com.mkt.admin");
    }
}
