package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * AT-C01 fixture proof: illegal wall-clock {@code now()} forms fail; injected Clock passes.
 *
 * <p>F13 closes the access-form gap where only Instant.now() was fixture-proven while
 * LocalDate.now() and LocalDateTime.now(ZoneId) slipped past the no-arg-only gate.
 */
class ClockDirectCallArchTest {

    private static final ArchRule NO_INSTANT_NOW = noClasses()
            .that()
            .resideInAPackage("com.mkt.admin.arch.fixture.clock..")
            .should()
            .callMethod(Instant.class, "now");

    private static final ArchRule NO_LOCAL_DATE_NOW = noClasses()
            .that()
            .resideInAPackage("com.mkt.admin.arch.fixture.clock..")
            .should()
            .callMethod(LocalDate.class, "now");

    private static final ArchRule NO_LOCAL_DATE_TIME_NOW_ZONE = noClasses()
            .that()
            .resideInAPackage("com.mkt.admin.arch.fixture.clock..")
            .should()
            .callMethod(LocalDateTime.class, "now", ZoneId.class);

    @Test
    void illegalDirectNowFails() {
        JavaClasses illegal =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.clock.illegal");
        assertThatThrownBy(() -> NO_INSTANT_NOW.check(illegal)).isInstanceOf(AssertionError.class);
    }

    @Test
    void illegalLocalDateNowFails() {
        JavaClasses illegal =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.clock.illegal");
        assertThatThrownBy(() -> NO_LOCAL_DATE_NOW.check(illegal)).isInstanceOf(AssertionError.class);
    }

    @Test
    void illegalLocalDateTimeNowZoneIdFails() {
        JavaClasses illegal =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.clock.illegal");
        assertThatThrownBy(() -> NO_LOCAL_DATE_TIME_NOW_ZONE.check(illegal))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void injectedClockPasses() {
        JavaClasses legal =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.clock.legal");
        NO_INSTANT_NOW.allowEmptyShould(true).check(legal);
        NO_LOCAL_DATE_NOW.allowEmptyShould(true).check(legal);
        NO_LOCAL_DATE_TIME_NOW_ZONE.allowEmptyShould(true).check(legal);
    }
}
