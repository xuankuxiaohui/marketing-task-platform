package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.mkt.admin.arch.ProductionClasses;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * AT-C01: no direct wall-clock calls (design §7.2 / §7.6).
 *
 * <p>F13: previously only Instant/LocalDateTime no-arg {@code now()} and {@code
 * System.currentTimeMillis} were gated; LocalDate / ZonedDateTime / OffsetDateTime and {@code
 * now(ZoneId)} overloads were a blind access form. {@code Clock.systemUTC()} at wiring sites and
 * {@code System.nanoTime()} for audit duration remain intentional exceptions.
 */
class ArchClockRuleTest {

    @Test
    void atC01_forbidsDirectNowAndCurrentTimeMillis() {
        noClasses()
                .that()
                .resideInAPackage("com.mkt..")
                .should()
                .callMethod(Instant.class, "now")
                .orShould()
                .callMethod(LocalDateTime.class, "now")
                .orShould()
                .callMethod(LocalDateTime.class, "now", ZoneId.class)
                .orShould()
                .callMethod(LocalDate.class, "now")
                .orShould()
                .callMethod(LocalDate.class, "now", ZoneId.class)
                .orShould()
                .callMethod(ZonedDateTime.class, "now")
                .orShould()
                .callMethod(ZonedDateTime.class, "now", ZoneId.class)
                .orShould()
                .callMethod(OffsetDateTime.class, "now")
                .orShould()
                .callMethod(OffsetDateTime.class, "now", ZoneId.class)
                .orShould()
                .callMethod(System.class, "currentTimeMillis")
                .because("time-sensitive code must inject java.time.Clock (D-03 / AT-C01)")
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }
}
