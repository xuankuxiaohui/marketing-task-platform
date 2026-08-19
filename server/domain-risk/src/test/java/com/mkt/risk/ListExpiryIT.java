package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.risk.command.RiskListItemCreateCommand;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.it.RiskITSupport;
import com.mkt.risk.query.RiskListItemQuery;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R25.2: expired list rows stop intercepting claim and login. Requires Docker; leave for CI. */
@Testcontainers
class ListExpiryIT {

    @Container
    static final MySQLContainer<?> MYSQL = RiskITSupport.mysql();

    @Container
    static final GenericContainer<?> REDIS = RiskITSupport.redis();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void expiredEntriesRestoreClaimAndLogin() {
        Instant start = Instant.parse("2026-08-19T10:00:00Z");
        try (RiskITSupport env = RiskITSupport.start(MYSQL, REDIS, start)) {
            UserContext.set(new UserPrincipal(1L, "admin", "op"));
            Instant expireAt = start.plusSeconds(30);

            env.tx.executeWithoutResult(status -> env.lists.add(new RiskListItemCreateCommand(
                    RiskDimension.USER, RiskListType.BLACK, "501", "temp", expireAt, false, null)));
            env.tx.executeWithoutResult(status -> env.lists.add(new RiskListItemCreateCommand(
                    RiskDimension.IP, RiskListType.BLACK, "10.0.0.1", "temp-ip", expireAt, false, null)));
            env.tx.executeWithoutResult(status -> env.lists.add(new RiskListItemCreateCommand(
                    RiskDimension.USER, RiskListType.BLACK, "502", "deny", expireAt, true, null)));

            var userPage = env.lists.page(new RiskListItemQuery(
                    RiskDimension.USER, null, null, null, null, PageQuery.of(1, 20)));
            assertThat(userPage.total()).isEqualTo(2);
            assertThat(userPage.records()).extracting(row -> row.listValue()).containsExactlyInAnyOrder("501", "502");

            RiskSubject claimer = new RiskSubject(501L, "8.8.8.8", "dev-501", null);
            RiskSubject ipLogin = new RiskSubject(900L, "10.0.0.1", "dev-900", null);
            RiskSubject denyLogin = new RiskSubject(502L, "9.9.9.9", "dev-502", null);

            assertThat(env.lookup.decide(RiskScene.CLAIM, claimer)).isEqualTo(ListDecision.REJECT);
            assertThat(env.lookup.decide(RiskScene.LOGIN, ipLogin)).isEqualTo(ListDecision.REJECT);
            assertThat(env.lookup.decide(RiskScene.LOGIN, denyLogin)).isEqualTo(ListDecision.REJECT);

            env.tx.executeWithoutResult(status -> env.lists.add(new RiskListItemCreateCommand(
                    RiskDimension.IP, RiskListType.BLACK, "2001:DB8::1", "v6", expireAt, false, null)));
            assertThat(env.lookup.decide(
                            RiskScene.LOGIN, new RiskSubject(901L, "2001:db8::1", "dev-v6", null)))
                    .isEqualTo(ListDecision.REJECT);

            var byType = env.lists.page(new RiskListItemQuery(
                    null, RiskListType.BLACK, "501", null, null, PageQuery.of(1, 20)));
            assertThat(byType.total()).isEqualTo(1);
            var byTime = env.lists.page(new RiskListItemQuery(
                    null, null, null, start.minusSeconds(1), start.plusSeconds(1), PageQuery.of(1, 20)));
            assertThat(byTime.total()).isGreaterThanOrEqualTo(3);

            env.clock.setInstant(expireAt.plusSeconds(1));
            assertThat(env.lookup.decide(RiskScene.CLAIM, claimer)).isEqualTo(ListDecision.PASS);
            assertThat(env.lookup.decide(RiskScene.LOGIN, ipLogin)).isEqualTo(ListDecision.PASS);
            assertThat(env.lookup.decide(RiskScene.LOGIN, denyLogin)).isEqualTo(ListDecision.PASS);
            assertThat(env.lookup.decide(
                            RiskScene.LOGIN, new RiskSubject(901L, "2001:db8::1", "dev-v6", null)))
                    .isEqualTo(ListDecision.PASS);
        }
    }
}
