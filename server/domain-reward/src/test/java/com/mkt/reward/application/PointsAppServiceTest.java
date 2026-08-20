package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PointsAdjustCommand;
import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.query.PointsAccountQuery;
import com.mkt.reward.query.PointsTransactionQuery;
import com.mkt.reward.support.PointsErrorCodes;
import com.mkt.reward.testsupport.MemoryPointsStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PointsAppServiceTest {

    private MemoryPointsStore store;
    private PointsAppService points;

    @BeforeEach
    void setUp() {
        store = new MemoryPointsStore();
        points = new PointsAppService(
                store, (org.springframework.transaction.PlatformTransactionManager) null, Clock.systemUTC());
    }

    @Test
    void earnLazyCreatesAccountAndWritesLedger() {
        long txId = points.earn(9L, 40, Instant.parse("2026-08-21T00:00:00Z"), "TASK_STEP", "s-1");
        assertThat(txId).isPositive();
        assertThat(points.balanceOrZero(9L)).isEqualTo(40L);
        assertThat(store.getByUserId(9L).getBalance()).isEqualTo(40L);
        assertThat(store.listChronological(9L)).hasSize(1);
        assertThat(store.listChronological(9L).getFirst().getType()).isEqualTo(PointTypes.EARN);
        assertThat(store.listChronological(9L).getFirst().getAmount()).isEqualTo(40L);
        assertThat(store.listChronological(9L).getFirst().getBalanceAfter()).isEqualTo(40L);
    }

    @Test
    void adjustCreditCreatesAccount() {
        assertThat(points.adjust(new PointsAdjustCommand(3L, 15L, "gift")).balance()).isEqualTo(15L);
        assertThat(points.balanceOrZero(3L)).isEqualTo(15L);
    }

    @Test
    void adjustDebitWithoutAccountIsNotFound() {
        assertThatThrownBy(() -> points.adjust(new PointsAdjustCommand(3L, -1L, "take")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(PointsErrorCodes.ACCOUNT_NOT_FOUND);
        assertThat(store.listChronological(3L)).isEmpty();
    }

    @Test
    void adjustDebitRejectedWhenInsufficientLeavesNoRow() {
        points.earn(3L, 5, null, "TASK_STEP", "s");
        assertThatThrownBy(() -> points.adjust(new PointsAdjustCommand(3L, -9L, "over")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(PointsErrorCodes.INSUFFICIENT_BALANCE);
        assertThat(points.balanceOrZero(3L)).isEqualTo(5L);
        assertThat(store.listChronological(3L)).hasSize(1);
    }

    @Test
    void adjustRequiresReason() {
        assertThatThrownBy(() -> points.adjust(new PointsAdjustCommand(3L, 1L, "  ")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(PointsErrorCodes.REASON_REQUIRED);
    }

    @Test
    void expireTruncatesToZeroAndMarksEarn() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);
        points = new PointsAppService(store, (org.springframework.transaction.PlatformTransactionManager) null, clock);
        points.earn(8L, 30, Instant.parse("2026-08-19T00:00:00Z"), "TASK_STEP", "e1");
        points.adjust(new PointsAdjustCommand(8L, -12L, "spend"));
        assertThat(points.expireDue()).isEqualTo(1);
        assertThat(points.balanceOrZero(8L)).isZero();
        var rows = store.listChronological(8L);
        assertThat(rows).hasSize(3);
        var expire = rows.get(2);
        assertThat(expire.getType()).isEqualTo(PointTypes.EXPIRE);
        assertThat(expire.getAmount()).isEqualTo(-18L);
        assertThat(expire.getBalanceAfter()).isZero();
        assertThat(expire.getBizId()).isEqualTo(String.valueOf(rows.getFirst().getId()));
        assertThat(expire.getRemark()).contains("truncated");
        assertThat(points.expireDue()).isZero();
    }

    @Test
    void balanceReadInsertsAccount() {
        assertThat(points.balance(4L).balance()).isZero();
        assertThat(store.getByUserId(4L)).isNotNull();
    }

    @Test
    void userSummaryPathDoesNotCreateAccount() {
        assertThat(points.balanceOrZero(4L)).isZero();
        assertThat(store.getByUserId(4L)).isNull();
    }

    @Test
    void consumeAndReverseDebitTheLedger() {
        points.earn(7L, 20, null, "TASK_STEP", "e");
        assertThat(points.consume(7L, 5, "SIGNIN", "c", "makeup")).isEqualTo(15L);
        assertThat(points.reverse(7L, 4, "TASK_STEP", "r", "undo")).isEqualTo(11L);
        assertThatThrownBy(() -> points.consume(7L, 40, "SIGNIN", "x", "over"))
                .isInstanceOf(BusinessException.class);
        assertThat(store.listChronological(7L)).hasSize(3);
    }

    @Test
    void expireExactAmountHasNoRemark() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC);
        points = new PointsAppService(store, (org.springframework.transaction.PlatformTransactionManager) null, clock);
        points.earn(8L, 10, Instant.parse("2026-08-19T00:00:00Z"), "TASK_STEP", "e1");
        assertThat(points.expireDue()).isEqualTo(1);
        var expire = store.listChronological(8L).get(1);
        assertThat(expire.getAmount()).isEqualTo(-10L);
        assertThat(expire.getRemark()).isNull();
        assertThat(points.expireDue()).isZero();
    }

    @Test
    void portalAndAdminPagesFilterByOwner() {
        points.earn(1L, 10, null, "TASK_STEP", "a");
        points.earn(2L, 7, null, "TASK_STEP", "b");
        assertThat(points.pagePortal(1L, null, 1, 20).total()).isEqualTo(1);
        assertThat(points.pageAccounts(new PointsAccountQuery(1L, PageQuery.of(1, 20))).total())
                .isEqualTo(1);
        assertThat(points.pageTransactions(new PointsTransactionQuery(2L, PointTypes.EARN, null, null, PageQuery.of(1, 20)))
                        .records()
                        .getFirst()
                        .amount())
                .isEqualTo(7L);
    }
}
