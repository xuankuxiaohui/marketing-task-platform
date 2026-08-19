package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.risk.command.RiskCaseHandleCommand;
import com.mkt.risk.domain.RiskHandleAction;
import com.mkt.risk.it.RiskITSupport;
import com.mkt.risk.query.RiskHitQuery;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R27.1: each successful handle writes exactly one handle_log and one audit.log. Requires Docker; leave for CI. */
@Testcontainers
class CaseHandleAuditIT {

    @Container
    static final MySQLContainer<?> MYSQL = RiskITSupport.mysql();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void handleSequenceMatchesHandleLogAndAudit() {
        Instant start = Instant.parse("2026-08-19T12:00:00Z");
        try (RiskITSupport env = RiskITSupport.start(MYSQL, start)) {
            UserContext.set(new UserPrincipal(1L, "admin", "op"));

            env.tx.executeWithoutResult(status -> env.cases.handle(
                    new RiskCaseHandleCommand(null, 800L, RiskHandleAction.ADD_BLACK, false, "add", null)));
            env.tx.executeWithoutResult(status -> env.cases.handle(
                    new RiskCaseHandleCommand(null, 800L, RiskHandleAction.REMOVE_BLACK, true, "unban", null)));
            env.tx.executeWithoutResult(status -> env.cases.handle(
                    new RiskCaseHandleCommand(null, 800L, RiskHandleAction.MARK_FALSE_POSITIVE, false, "fp", null)));

            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.cases.handle(
                            new RiskCaseHandleCommand(null, 800L, RiskHandleAction.ADD_BLACK, false, "  ", null))))
                    .isInstanceOf(BusinessException.class);

            Integer handles = env.jdbc.queryForObject("SELECT COUNT(*) FROM risk_handle_log", Integer.class);
            Integer audits = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_outbox WHERE event_code = 'audit.log'", Integer.class);
            assertThat(handles).isEqualTo(3);
            assertThat(audits).isEqualTo(3);
            assertThat(env.handleStore.listAll())
                    .extracting(row -> row.getAction())
                    .containsExactly("ADD_BLACK", "REMOVE_BLACK", "MARK_FALSE_POSITIVE");
            assertThat(env.handleStore.listAll())
                    .allMatch(row -> row.getOperatorId() == 1L
                            && row.getUserId() == 800L
                            && row.getReason() != null
                            && !row.getReason().isBlank()
                            && row.getCreatedAt() != null);
            env.jdbc.update(
                    """
                    INSERT INTO risk_hit_log
                    (id, hit_type, rule_code, user_id, dimension_value, context, hit_value, threshold, action_result, simulated, occurred_at)
                    VALUES (9001, 'LIST', 'USER:BLACK', 800, NULL, CAST('{}' AS JSON), '1', '1', 'REJECTED', 0, ?)
                    """,
                    java.sql.Timestamp.from(start));
            var hits = env.cases.pageHits(new RiskHitQuery(
                    "USER:BLACK",
                    "LIST",
                    null,
                    800L,
                    "REJECTED",
                    start.minusSeconds(1),
                    start.plusSeconds(1),
                    PageQuery.of(1, 10)));
            assertThat(hits.total()).isEqualTo(1);
            assertThat(hits.records().get(0).ruleCode()).isEqualTo("USER:BLACK");
        }
    }
}
