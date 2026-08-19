package com.mkt.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContractRecordsTest {

    @Test
    void grantContextDefaultsSimulatedFalse() {
        GrantContext ctx = GrantContext.defaults();
        assertThat(ctx.simulated()).isFalse();
        assertThat(ctx.bypassRules()).isEmpty();
        assertThat(ctx.reason()).isNull();
        assertThat(ctx.elapsedSeconds()).isNull();
    }

    @Test
    void userAttributesNotFoundHasNullProfile() {
        UserAttributes missing = UserAttributes.notFound();
        assertThat(missing.accountStatus()).isEqualTo(AccountStatus.NOT_FOUND);
        assertThat(missing.province()).isNull();
        assertThat(missing.tags()).isEmpty();
        assertThatThrownBy(
                        () -> new UserAttributes(null, null, null, null, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void riskSubjectRequiresIp() {
        assertThatThrownBy(() -> new RiskSubject(1L, " ", "dev", null))
                .isInstanceOf(IllegalArgumentException.class);
        RiskSubject subject = new RiskSubject(1L, "127.0.0.1", null, null);
        assertThat(subject.ip()).isEqualTo("127.0.0.1");
    }

    @Test
    void grantExceptionsCarryClosedReasons() {
        PermanentGrantException permanent = new PermanentGrantException(PermanentGrantReason.USER_INVALID);
        assertThat(permanent.reason()).isEqualTo(PermanentGrantReason.USER_INVALID);
        RetryableGrantException retryable = new RetryableGrantException(RetryableGrantReason.STOCK_INSUFFICIENT);
        assertThat(retryable.reason()).isEqualTo(RetryableGrantReason.STOCK_INSUFFICIENT);
    }

    @Test
    void closedEnumsMatchDesignTables() {
        assertThat(GrantSource.values())
                .containsExactly(
                        GrantSource.TASK_STEP,
                        GrantSource.SIGNIN_DAY,
                        GrantSource.ACTIVITY_PARTICIPATION,
                        GrantSource.MANUAL_GRANT,
                        GrantSource.SIMULATE);
        assertThat(GrantStatus.values())
                .containsExactly(
                        GrantStatus.PENDING,
                        GrantStatus.WON,
                        GrantStatus.CLAIMING,
                        GrantStatus.GRANTED,
                        GrantStatus.RETRY_PENDING,
                        GrantStatus.PERMANENT_FAILED,
                        GrantStatus.EXPIRED);
        assertThat(FulfillmentStatus.values())
                .containsExactly(
                        FulfillmentStatus.NONE,
                        FulfillmentStatus.SENDING,
                        FulfillmentStatus.ARRIVED,
                        FulfillmentStatus.FULFILL_FAILED);
        assertThat(BypassRule.values()).containsExactly(BypassRule.REGION, BypassRule.LEVEL, BypassRule.TAG);
        assertThat(RiskScene.values())
                .containsExactly(RiskScene.REGISTER, RiskScene.LOGIN, RiskScene.CLAIM, RiskScene.GRANT);
        assertThat(RiskAction.values())
                .containsExactly(RiskAction.PASS, RiskAction.REJECT, RiskAction.SILENT_REJECT, RiskAction.MARK);
        assertThat(RiskListType.values()).containsExactly(RiskListType.BLACK, RiskListType.WHITE);
        assertThat(AccountStatus.values())
                .containsExactly(
                        AccountStatus.ACTIVE,
                        AccountStatus.DISABLED,
                        AccountStatus.DELETED,
                        AccountStatus.NOT_FOUND);
        assertThat(PermanentGrantReason.values())
                .containsExactly(
                        PermanentGrantReason.PRIZE_DISABLED,
                        PermanentGrantReason.PRIZE_DELETED,
                        PermanentGrantReason.USER_INVALID);
        assertThat(RetryableGrantReason.values())
                .containsExactly(RetryableGrantReason.SYSTEM_ERROR, RetryableGrantReason.STOCK_INSUFFICIENT);
    }

    @Test
    void summaryRecordsHoldSpecifiedFields() {
        GrantResult grant = new GrantResult(9L, GrantStatus.WON, FulfillmentStatus.NONE, 3L, true);
        assertThat(grant.hitIdempotent()).isTrue();
        assertThat(grant.status()).isEqualTo(GrantStatus.WON);

        UserRewardSummary rewards = new UserRewardSummary(100L, new PrizeSummary(1L, 2L));
        assertThat(rewards.pointsBalance()).isEqualTo(100L);
        assertThat(rewards.prizeSummary().won()).isEqualTo(1L);

        UserRiskSummary risk = new UserRiskSummary(2L, List.of(RiskListType.BLACK));
        assertThat(risk.hitCount()).isEqualTo(2L);
        assertThat(risk.listStatus()).containsExactly(RiskListType.BLACK);
        assertThat(new UserRiskSummary(0L, null).listStatus()).isEmpty();

        InstanceCounts counts = new InstanceCounts(1L, 4L);
        assertThat(counts.inProgressInstanceCount()).isEqualTo(1L);
        assertThat(counts.historyInstanceCount()).isEqualTo(4L);

        RiskVerdict verdict = new RiskVerdict(RiskAction.PASS);
        assertThat(verdict.action()).isEqualTo(RiskAction.PASS);
        assertThatThrownBy(() -> new RiskVerdict(null)).isInstanceOf(IllegalArgumentException.class);

        GrantContext withRules =
                new GrantContext("manual", List.of(BypassRule.REGION), 8L, false, 12L);
        assertThat(withRules.bypassRules()).containsExactly(BypassRule.REGION);
        assertThat(withRules.elapsedSeconds()).isEqualTo(12L);
        assertThat(new GrantContext(null, null, null, false, null).bypassRules()).isEmpty();
    }
}
