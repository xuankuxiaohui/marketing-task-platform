package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.GrantSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R17.3: grant costFen/faceFen stay frozen after prize face/unit changes. */
@Testcontainers
class CostSnapshotIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void changingPrizeFaceDoesNotMutateGrantSnapshotOrSpendSum() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            long prizeId = env.enableAlipay("red_snap_" + System.nanoTime(), 5, 0, 0, 88);
            var result = env.tx.execute(
                    status -> env.stock.consume(prizeId, 11L, GrantSource.TASK_STEP, "snap-1"));
            env.jdbc.update(
                    "UPDATE rwd_grant_record SET fulfillment_status = 'ARRIVED' WHERE id = ?",
                    result.grantRecordId());
            Integer costBefore = env.jdbc.queryForObject(
                    "SELECT cost_fen FROM rwd_grant_record WHERE id = ?", Integer.class, result.grantRecordId());
            Integer faceBefore = env.jdbc.queryForObject(
                    "SELECT face_fen FROM rwd_grant_record WHERE id = ?", Integer.class, result.grantRecordId());
            assertThat(costBefore).isEqualTo(88);
            assertThat(faceBefore).isEqualTo(88);

            env.jdbc.update(
                    "UPDATE rwd_prize SET type_params = ?, unit_cost_fen = 1 WHERE id = ?",
                    "{\"faceFen\": 1}",
                    prizeId);

            Integer costAfter = env.jdbc.queryForObject(
                    "SELECT cost_fen FROM rwd_grant_record WHERE id = ?", Integer.class, result.grantRecordId());
            Integer faceAfter = env.jdbc.queryForObject(
                    "SELECT face_fen FROM rwd_grant_record WHERE id = ?", Integer.class, result.grantRecordId());
            Integer spend = env.jdbc.queryForObject(
                    """
                    SELECT COALESCE(SUM(cost_fen), 0) FROM rwd_grant_record
                    WHERE prize_id = ? AND simulated = 0 AND fulfillment_status = 'ARRIVED'
                    """,
                    Integer.class,
                    prizeId);
            assertThat(costAfter).isEqualTo(88);
            assertThat(faceAfter).isEqualTo(88);
            assertThat(spend).isEqualTo(88);
        }
    }
}
