package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconResults;
import com.mkt.reward.response.FulfillmentCallbackResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R37.2: reissue closes original; original fulfillmentRef callback stays failed. */
@Testcontainers
class ReconReissueIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void manualGrantThenOriginalCallbackDoesNotArrive() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            UserContext.set(new UserPrincipal(7L, "admin", "op"));
            long prizeId = env.enableAlipay("red_re_" + System.nanoTime(), 10, 0, 0, 100);
            long original = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "orig", GrantContext.defaults()))
                    .recordId();
            env.jdbc.update(
                    "UPDATE rwd_grant_record SET fulfillment_ref=?, fulfillment_status='FULFILL_FAILED', fulfill_fail_reason='CHANNEL_REJECT', recon_status='PENDING' WHERE id=?",
                    "alipay-red:orig",
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
            env.tx.executeWithoutResult(status -> env.recon.action(
                    itemId, new ReconActionCommand(ReconActions.MANUAL_GRANT, "补发", 9L, prizeId)));
            String reason = env.jdbc.queryForObject(
                    "SELECT fulfill_fail_reason FROM rwd_grant_record WHERE id=?", String.class, original);
            assertThat(reason).isEqualTo(FulfillFailReasons.MANUAL);
            FulfillmentCallbackResponse callback =
                    env.tx.execute(status -> env.fulfillment.callback("alipay-red:orig", "SUCCESS", null));
            assertThat(callback.superseded()).isTrue();
            String status = env.jdbc.queryForObject(
                    "SELECT fulfillment_status FROM rwd_grant_record WHERE id=?", String.class, original);
            assertThat(status).isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
            Integer arrived = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE prize_id=? AND fulfillment_status='ARRIVED'",
                    Integer.class,
                    prizeId);
            assertThat(arrived).isLessThanOrEqualTo(1);
            UserContext.clear();
        }
    }
}
