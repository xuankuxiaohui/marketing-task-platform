package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.command.ReconImportLineCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.response.ReconMatchResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R37.1: one batch covers every platform PENDING row and every imported channel line. */
@Testcontainers
class ReconExhaustiveIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void fourWayMatchHasNoGapOrDoubleCount() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            UserContext.set(new UserPrincipal(7L, "admin", "op"));
            long prizeId = env.enableAlipay("red_exh_" + System.nanoTime(), 10, 0, 0, 100);
            long arrived = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "a", GrantContext.defaults()))
                    .recordId();
            long sending = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "s", GrantContext.defaults()))
                    .recordId();
            long failed = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "f", GrantContext.defaults()))
                    .recordId();
            env.jdbc.update(
                    "UPDATE rwd_grant_record SET fulfillment_ref=?, fulfillment_status='ARRIVED', recon_status='PENDING' WHERE id=?",
                    "alipay-red:a",
                    arrived);
            env.jdbc.update(
                    "UPDATE rwd_grant_record SET fulfillment_ref=?, fulfillment_status='SENDING', recon_status='PENDING' WHERE id=?",
                    "alipay-red:s",
                    sending);
            env.jdbc.update(
                    "UPDATE rwd_grant_record SET fulfillment_ref=?, fulfillment_status='FULFILL_FAILED', fulfill_fail_reason='CHANNEL_REJECT', recon_status='PENDING', cost_fen=80 WHERE id=?",
                    "alipay-red:f",
                    failed);
            LocalDate bill = LocalDate.of(2026, 8, 19);
            long batchId = env.tx.execute(status -> env.recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, bill)))
                    .id();
            env.tx.executeWithoutResult(status -> env.recon.importLines(
                    batchId,
                    new ReconImportCommand(List.of(
                            new ReconImportLineCommand("alipay-red:a", 100, null),
                            new ReconImportLineCommand("alipay-red:f", 90, null),
                            new ReconImportLineCommand("alipay-red:x", 10, null)))));
            ReconMatchResponse result = env.tx.execute(status -> env.recon.match(batchId));
            assertThat(result.matchedCount()).isEqualTo(1);
            assertThat(result.platformOnly()).isEqualTo(1);
            assertThat(result.channelOnly()).isEqualTo(1);
            assertThat(result.amountMismatch()).isEqualTo(1);
            Integer items = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_recon_item WHERE batch_id=?", Integer.class, batchId);
            assertThat(items).isEqualTo(4);
            UserContext.clear();
        }
    }
}
