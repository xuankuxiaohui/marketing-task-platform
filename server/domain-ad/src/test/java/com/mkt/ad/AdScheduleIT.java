package com.mkt.ad;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R30.2: clock outside schedule → pull contains no material for any subject. */
@Testcontainers
class AdScheduleIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void outsideScheduleYieldsEmptyForLoggedInAndAnonymous() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-20T04:00:00Z"));
        try (AdITSupport env = new AdITSupport(MYSQL, clock)) {
            env.tx.execute(status -> env.publishImage("home_image", 20));
            assertThat(env.portal.pull("home_image", 9L, "dev-1", "WEB").materials()).isNotEmpty();
            clock.setInstant(Instant.parse("2026-09-01T00:00:00Z"));
            assertThat(env.portal.pull("home_image", 9L, "dev-1", "WEB").materials()).isEmpty();
            assertThat(env.portal.pull("home_image", null, "dev-1", "WEB").materials()).isEmpty();
        }
    }
}
