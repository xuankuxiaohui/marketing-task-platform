package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconResults;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R37.3: TIMEOUT/SENDING without CONFIRMED refuse refulfill and manual-grant; AUTO never grants. */
@Testcontainers
class ReconReviewGateIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void timeoutUnreviewedRefusesActionsAndAutoCreatesNoGrant() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            UserContext.set(new UserPrincipal(7L, "admin", "op"));
            env.runtime.setAutoRefulfillEnabled(true);
            long prizeId = env.enableAlipay("red_gate_" + System.nanoTime(), 10, 0, 0, 100);
            long original = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "to", GrantContext.defaults()))
                    .recordId();
            env.jdbc.update(
                    "UPDATE rwd_grant_record SET fulfillment_ref=?, fulfillment_status='FULFILL_FAILED', fulfill_fail_reason='TIMEOUT', recon_status='PENDING' WHERE id=?",
                    "alipay-red:to",
                    original);
            LocalDate bill = LocalDate.of(2026, 8, 19);
            long batchId = env.tx.execute(status -> env.recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, bill)))
                    .id();
            env.tx.executeWithoutResult(status -> env.recon.match(batchId));
            Long itemId = env.jdbc.queryForObject(
                    "SELECT id FROM rwd_recon_item WHERE batch_id=? AND result=?",
                    Long.class,
                    batchId,
                    ReconResults.PLATFORM_ONLY);
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.recon.action(
                            itemId, new ReconActionCommand(ReconActions.REFULFILL, "x", null, null))))
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(RewardErrorCodes.RECON_REVIEW_REQUIRED);
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.recon.action(
                            itemId, new ReconActionCommand(ReconActions.MANUAL_GRANT, "x", 9L, prizeId))))
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(RewardErrorCodes.RECON_REVIEW_REQUIRED);
            Integer manuals = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE grant_source='MANUAL_GRANT'", Integer.class);
            assertThat(manuals).isZero();
            Integer remaining = env.jdbc.queryForObject(
                    "SELECT remaining_stock FROM rwd_prize WHERE id=?", Integer.class, prizeId);
            assertThat(remaining).isEqualTo(9);
            UserContext.clear();
        }
    }
}
