package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** AT-C01 fixture proof: illegal Instant.now() fails; injected Clock passes. */
class ClockDirectCallArchTest {

    private static final ArchRule NO_INSTANT_NOW = noClasses()
            .that()
            .resideInAPackage("com.mkt.admin.arch.fixture.clock..")
            .should()
            .callMethod(Instant.class, "now");

    @Test
    void illegalDirectNowFails() {
        JavaClasses illegal =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.clock.illegal");
        assertThatThrownBy(() -> NO_INSTANT_NOW.check(illegal)).isInstanceOf(AssertionError.class);
    }

    @Test
    void injectedClockPasses() {
        JavaClasses legal =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.clock.legal");
        NO_INSTANT_NOW.allowEmptyShould(true).check(legal);
    }
}
