package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.mkt.admin.arch.ProductionClasses;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** AT-C01: no direct clock calls (design §7.2 / §7.6). */
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
                .callMethod(System.class, "currentTimeMillis")
                .because("time-sensitive code must inject java.time.Clock (D-03 / AT-C01)")
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }
}
