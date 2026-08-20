package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.testsupport.CategoryFixtures;
import com.mkt.reward.testsupport.MemoryGrantRecordStore;
import com.mkt.reward.testsupport.MemoryPrizeCategoryStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import com.mkt.reward.testsupport.MemorySnapshotPrizeLookup;
import com.mkt.reward.testsupport.MemoryStockLogStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrizeStockServiceTest {

    private MemoryPrizeStore prizes;
    private MemoryPrizeCategoryStore categories;
    private MemoryGrantRecordStore grants;
    private MemoryStockLogStore logs;
    private PrizeAppService prizesApp;
    private PrizeStockService stock;

    @BeforeEach
    void setUp() {
        prizes = new MemoryPrizeStore();
        categories = new MemoryPrizeCategoryStore();
        grants = new MemoryGrantRecordStore();
        logs = new MemoryStockLogStore();
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        prizesApp = new PrizeAppService(prizes, categories, logs, new MemorySnapshotPrizeLookup(), clock);
        stock = new PrizeStockService(prizes, categories, grants, logs, clock);
        categories.seed(CategoryFixtures.alipay());
        categories.seed(CategoryFixtures.points());
    }

    @Test
    void consumeWritesGrantSnapshotAndStockLog() {
        long prizeId = enableAlipay("red_s", 3, 100);
        var result = stock.consume(prizeId, 9L, GrantSource.TASK_STEP, "step-1");
        assertThat(result.remainingStock()).isEqualTo(2);
        assertThat(result.costFen()).isEqualTo(100);
        assertThat(result.faceFen()).isEqualTo(100);
        assertThat(grants.getById(result.grantRecordId()).getCostFen()).isEqualTo(100);
        assertThat(logs.all()).hasSize(1);
        assertThat(logs.all().get(0).getChangeType()).isEqualTo("GRANT");
        assertThat(logs.all().get(0).getAmount()).isEqualTo(-1);
        assertThat(logs.all().get(0).getBeforeValue()).isEqualTo(3);
        assertThat(logs.all().get(0).getAfterValue()).isEqualTo(2);
    }

    @Test
    void consumeRejectsWhenStockGone() {
        long prizeId = enableAlipay("red_t", 1, 50);
        stock.consume(prizeId, 1L, GrantSource.TASK_STEP, "a");
        assertThatThrownBy(() -> stock.consume(prizeId, 2L, GrantSource.TASK_STEP, "b"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.STOCK_INSUFFICIENT);
    }

    @Test
    void dailyLimitUsesDistinctMessage() {
        long prizeId = enableAlipayLimited("red_u", 10, 1, 0);
        stock.consume(prizeId, 9L, GrantSource.TASK_STEP, "d1");
        assertThatThrownBy(() -> stock.consume(prizeId, 9L, GrantSource.TASK_STEP, "d2"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException biz = (BusinessException) ex;
                    assertThat(biz.errorCode()).isEqualTo(RewardErrorCodes.CLAIM_LIMIT_EXCEEDED);
                    assertThat(biz.getMessage()).contains("当日");
                });
    }

    @Test
    void restoreReturnsStockAndDropsLimitSlot() {
        long prizeId = enableAlipay("red_v", 2, 80);
        var result = stock.consume(prizeId, 9L, GrantSource.TASK_STEP, "r1");
        stock.restore(prizeId, result.grantRecordId());
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(2);
        assertThat(grants.getById(result.grantRecordId()).getStatus()).isEqualTo("PERMANENT_FAILED");
        var again = stock.consume(prizeId, 9L, GrantSource.TASK_STEP, "r2");
        assertThat(again.remainingStock()).isEqualTo(1);
    }

    private long enableAlipay(String code, int stockQty, int faceFen) {
        return enableAlipayLimited(code, stockQty, 0, 0, faceFen);
    }

    private long enableAlipayLimited(String code, int stockQty, int daily, int total) {
        return enableAlipayLimited(code, stockQty, daily, total, 100);
    }

    private long enableAlipayLimited(String code, int stockQty, int daily, int total, int faceFen) {
        var created = prizesApp.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                BuiltinCategories.ALIPAY_RED,
                Map.of("faceFen", faceFen),
                null,
                stockQty,
                daily,
                total,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
                null,
                null));
        prizesApp.enable(created.id(), new PrizeConfirmCommand(true));
        return created.id();
    }
}
