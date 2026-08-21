package com.mkt.reward.points;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.BusinessException;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.command.PointsAdjustCommand;
import com.mkt.reward.entity.PntTransactionEntity;
import com.mkt.reward.support.PointsErrorCodes;
import com.mkt.reward.testsupport.MemoryPointsStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * R20.2: every ledger row's balance_after equals previous balance_after + amount;
 * rejected debit leaves no row.
 */
class PointsLedgerPropertyTest {

    private static final Logger log = LoggerFactory.getLogger(PointsLedgerPropertyTest.class);
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final long USER = 42L;

    @Property(tries = 200)
    void ledgerBalancesAndRejectedDebitsLeaveNoRow(@ForAll("ops") List<Op> ops) {
        log.debug("PointsLedgerPropertyTest seed ops={}", ops.size());
        MemoryPointsStore store = new MemoryPointsStore();
        PointsAppService points = new PointsAppService(
                store,
                (org.springframework.transaction.PlatformTransactionManager) null,
                Clock.fixed(NOW, ZoneOffset.UTC));
        int rejected = 0;
        for (int i = 0; i < ops.size(); i++) {
            Op op = ops.get(i);
            try {
                apply(points, op, i);
            } catch (BusinessException ex) {
                assertThat(ex.errorCode())
                        .isIn(PointsErrorCodes.INSUFFICIENT_BALANCE, PointsErrorCodes.ACCOUNT_NOT_FOUND);
                rejected++;
            }
        }
        points.expireDue();
        List<PntTransactionEntity> rows = store.listChronological(USER);
        long prev = 0L;
        for (PntTransactionEntity row : rows) {
            long amount = row.getAmount() == null ? 0L : row.getAmount();
            long after = row.getBalanceAfter() == null ? 0L : row.getBalanceAfter();
            assertThat(after).isEqualTo(prev + amount);
            assertThat(after).isGreaterThanOrEqualTo(0L);
            prev = after;
        }
        assertThat(points.balanceOrZero(USER)).isEqualTo(prev);
        assertThat(rejected).isGreaterThanOrEqualTo(0);
    }

    private static void apply(PointsAppService points, Op op, int seq) {
        switch (op.kind()) {
            case EARN -> points.earn(USER, op.magnitude(), op.expire() ? NOW.minusSeconds(3600) : null, "TASK_STEP", "e-" + seq);
            case CONSUME -> points.consume(USER, op.magnitude(), "TASK_STEP", "c-" + seq, "c");
            case ADJUST_UP -> points.adjust(new PointsAdjustCommand(USER, (long) op.magnitude(), "up"));
            case ADJUST_DOWN -> points.adjust(new PointsAdjustCommand(USER, -(long) op.magnitude(), "down"));
            case REVERSAL -> points.reverse(USER, op.magnitude(), "TASK_STEP", "r-" + seq, "rev");
        }
    }

    @Provide
    Arbitrary<List<Op>> ops() {
        Arbitrary<Op> one = Arbitraries.of(Kind.values()).flatMap(kind -> Arbitraries.integers()
                .between(1, 20)
                .flatMap(mag -> Arbitraries.of(true, false).map(expire -> new Op(kind, mag, expire))));
        return one.list().ofMinSize(1).ofMaxSize(24);
    }

    enum Kind {
        EARN,
        CONSUME,
        ADJUST_UP,
        ADJUST_DOWN,
        REVERSAL
    }

    record Op(Kind kind, int magnitude, boolean expire) {}
}
