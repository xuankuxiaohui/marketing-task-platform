package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.command.StockReplenishCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.ReconPolicies;
import com.mkt.reward.query.PrizeQuery;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.testsupport.CategoryFixtures;
import com.mkt.reward.testsupport.MemoryPrizeCategoryStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import com.mkt.reward.testsupport.MemorySnapshotPrizeLookup;
import com.mkt.reward.testsupport.MemoryStockLogStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrizeAppServiceTest {

    private MemoryPrizeStore prizes;
    private MemoryPrizeCategoryStore categories;
    private MemoryStockLogStore logs;
    private MemorySnapshotPrizeLookup snapshots;
    private PrizeAppService service;

    @BeforeEach
    void setUp() {
        prizes = new MemoryPrizeStore();
        categories = new MemoryPrizeCategoryStore();
        logs = new MemoryStockLogStore();
        snapshots = new MemorySnapshotPrizeLookup();
        service = new PrizeAppService(
                prizes,
                categories,
                logs,
                snapshots,
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        categories.seed(CategoryFixtures.points());
        categories.seed(CategoryFixtures.alipay());
        categories.seed(CategoryFixtures.physical());
        categories.seed(CategoryFixtures.coupon());
    }

    @Test
    void createCopiesTargetAndFulfillmentFromCategory() {
        var created = service.create(pointsCommand("pts_a", 100));
        assertThat(created.rewardTarget()).isEqualTo("PLATFORM");
        assertThat(created.fulfillmentMode()).isEqualTo("INSTANT");
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.remainingStock()).isEqualTo(100);
        assertThat(created.reconActionPolicy()).isNull();
    }

    @Test
    void pointsRequiresPositivePoints() {
        PrizeSaveCommand command = new PrizeSaveCommand(
                "pts_b",
                "积分B",
                null,
                null,
                BuiltinCategories.POINTS,
                Map.of(),
                null,
                10,
                0,
                0,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
                null,
                null);
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.PRIZE_POINTS_AMOUNT_REQUIRED);
    }

    @Test
    void faceValueRequiresFaceFen() {
        PrizeSaveCommand command = new PrizeSaveCommand(
                "red_a",
                "红包",
                null,
                null,
                BuiltinCategories.ALIPAY_RED,
                Map.of(),
                null,
                10,
                0,
                0,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
                null,
                null);
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.PRIZE_FACE_REQUIRED);
    }

    @Test
    void physicalRequiresUnitCost() {
        PrizeSaveCommand command = new PrizeSaveCommand(
                "phy_a",
                "杯子",
                null,
                null,
                BuiltinCategories.PHYSICAL,
                Map.of(),
                null,
                5,
                0,
                0,
                null,
                null,
                null,
                "MANUAL",
                null,
                null,
                null,
                null);
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.PRIZE_COST_REQUIRED);
    }

    @Test
    void disabledCategoryRejectsNewPrize() {
        categories.getByCode(BuiltinCategories.COUPON).setStatus("DISABLED");
        PrizeSaveCommand command = new PrizeSaveCommand(
                "cpn_a",
                "券",
                null,
                null,
                BuiltinCategories.COUPON,
                Map.of(),
                null,
                3,
                0,
                0,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
                null,
                null);
        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.PRIZE_CATEGORY_DISABLED);
    }

    @Test
    void enableDisableConfirmAndReconPolicyEditableAfterEnable() {
        var created = service.create(pointsCommand("pts_c", 20));
        service.enable(created.id(), new PrizeConfirmCommand(true));
        var preview = service.disable(created.id(), new PrizeConfirmCommand(false));
        assertThat(preview.confirmed()).isFalse();
        assertThat(service.get(created.id()).status()).isEqualTo("ENABLED");
        service.disable(created.id(), new PrizeConfirmCommand(true));
        assertThat(service.get(created.id()).status()).isEqualTo("DISABLED");

        service.update(
                created.id(),
                new PrizeSaveCommand(
                        "pts_c",
                        "积分C",
                        null,
                        null,
                        BuiltinCategories.POINTS,
                        Map.of("points", 10),
                        null,
                        20,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of(),
                        "AUTO",
                        ReconPolicies.AUTO,
                        null,
                        null,
                        null));
        assertThat(service.get(created.id()).reconActionPolicy()).isEqualTo(ReconPolicies.AUTO);
        assertThatThrownBy(() -> service.update(
                        created.id(),
                        new PrizeSaveCommand(
                                "pts_c",
                                "积分C",
                                null,
                                null,
                                BuiltinCategories.ALIPAY_RED,
                                Map.of("points", 10),
                                null,
                                20,
                                0,
                                0,
                                null,
                                null,
                                null,
                                "AUTO",
                                ReconPolicies.AUTO,
                                null,
                                null,
                                null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteDraftRejectedWhenSnapshotReferences() {
        var created = service.create(pointsCommand("pts_d", 8));
        snapshots.referenced.add(created.id());
        assertThatThrownBy(() -> service.delete(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.PRIZE_REFERENCED_BY_SNAPSHOT);
    }

    @Test
    void replenishWritesLogAndRaisesTotal() {
        var created = service.create(pointsCommand("pts_e", 10));
        service.enable(created.id(), new PrizeConfirmCommand(true));
        var result = service.replenish(created.id(), new StockReplenishCommand(5, "补货"));
        assertThat(result.remainingStock()).isEqualTo(15);
        assertThat(service.get(created.id()).totalStock()).isEqualTo(15);
        var page = service.stockLogs(created.id(), PageQuery.of(1, 20));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).changeType()).isEqualTo("REPLENISH");
        assertThat(page.records().get(0).amount()).isEqualTo(5);
        assertThat(page.records().get(0).beforeValue()).isEqualTo(10);
        assertThat(page.records().get(0).afterValue()).isEqualTo(15);
    }

    @Test
    void pageFiltersByCategory() {
        service.create(pointsCommand("pts_f", 2));
        var page = service.page(new PrizeQuery(null, null, BuiltinCategories.POINTS, null, null, PageQuery.of(1, 20)));
        assertThat(page.records()).extracting(row -> row.code()).contains("pts_f");
    }

    private static PrizeSaveCommand pointsCommand(String code, int stock) {
        return new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                BuiltinCategories.POINTS,
                Map.of("points", 10),
                null,
                stock,
                0,
                0,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
                null,
                null);
    }
}
