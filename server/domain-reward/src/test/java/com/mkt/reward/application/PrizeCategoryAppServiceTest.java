package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PrizeCategorySaveCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.ReconPolicies;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.testsupport.CategoryFixtures;
import com.mkt.reward.testsupport.MemoryPrizeCategoryStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrizeCategoryAppServiceTest {

    private MemoryPrizeCategoryStore store;
    private MemoryPrizeStore prizes;
    private PrizeCategoryAppService service;

    @BeforeEach
    void setUp() {
        store = new MemoryPrizeCategoryStore();
        prizes = new MemoryPrizeStore();
        service = new PrizeCategoryAppService(
                store, prizes, Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        store.seed(CategoryFixtures.points());
        store.seed(CategoryFixtures.alipay());
        store.seed(CategoryFixtures.physical());
        store.seed(CategoryFixtures.coupon());
    }

    @Test
    void pageIncludesBuiltinSeedsAndReconReview() {
        var page = service.page(PageQuery.of(1, 20));
        assertThat(page.total()).isGreaterThanOrEqualTo(4);
        assertThat(page.records())
                .anySatisfy(row -> {
                    assertThat(row.code()).isEqualTo(BuiltinCategories.ALIPAY_RED);
                    assertThat(row.reconRequired()).isTrue();
                    assertThat(row.reconActionPolicy()).isEqualTo(ReconPolicies.REVIEW);
                    assertThat(row.builtin()).isTrue();
                });
    }

    @Test
    void cannotDeleteBuiltin() {
        assertThatThrownBy(() -> service.delete(BuiltinCategories.POINTS))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.CATEGORY_BUILTIN_PROTECTED);
    }

    @Test
    void createCustomAndChangeReconPolicyWhileEnabled() {
        service.create(new PrizeCategorySaveCommand(
                "GIFT_CARD",
                "礼品卡",
                "THIRD_PARTY",
                "ASYNC",
                "FACE_VALUE",
                true,
                ReconPolicies.REVIEW,
                "gift-card",
                Map.of("faceFen", "正整数")));
        service.update(
                "GIFT_CARD",
                new PrizeCategorySaveCommand(
                        "GIFT_CARD",
                        "礼品卡",
                        "THIRD_PARTY",
                        "ASYNC",
                        "FACE_VALUE",
                        true,
                        ReconPolicies.AUTO,
                        "gift-card",
                        Map.of("faceFen", "正整数")));
        assertThat(service.get("GIFT_CARD").reconActionPolicy()).isEqualTo(ReconPolicies.AUTO);
        assertThat(service.get("GIFT_CARD").status()).isEqualTo("ENABLED");
    }

    @Test
    void disableThenRejectNewPrizeCategoryUse() {
        service.disable(BuiltinCategories.COUPON);
        assertThat(service.get(BuiltinCategories.COUPON).status()).isEqualTo("DISABLED");
    }

    @Test
    void builtinReconPolicyCanChangeWhileEnabled() {
        var alipay = service.get(BuiltinCategories.ALIPAY_RED);
        assertThat(alipay.reconActionPolicy()).isEqualTo(ReconPolicies.REVIEW);
        service.update(
                BuiltinCategories.ALIPAY_RED,
                new PrizeCategorySaveCommand(
                        BuiltinCategories.ALIPAY_RED,
                        alipay.name(),
                        alipay.rewardTarget(),
                        alipay.fulfillmentMode(),
                        alipay.costMode(),
                        alipay.reconRequired(),
                        ReconPolicies.AUTO,
                        alipay.adapterCode(),
                        alipay.paramSchema()));
        assertThat(service.get(BuiltinCategories.ALIPAY_RED).reconActionPolicy()).isEqualTo(ReconPolicies.AUTO);
        assertThat(service.get(BuiltinCategories.ALIPAY_RED).status()).isEqualTo("ENABLED");
    }

    @Test
    void cannotDeleteCategoryStillReferenced() {
        service.create(new PrizeCategorySaveCommand(
                "TOKEN_X", "代币", "PLATFORM", "INSTANT", "NONE", false, ReconPolicies.REVIEW, null, null));
        PrizeEntity prize = new PrizeEntity();
        prize.setCode("token_a");
        prize.setName("t");
        prize.setCategoryCode("TOKEN_X");
        prize.setStatus("DRAFT");
        prize.setDeleted(0);
        prizes.insert(prize);
        assertThatThrownBy(() -> service.delete("TOKEN_X")).isInstanceOf(BusinessException.class);
    }
}
