package com.mkt.reward.testsupport;

import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.CategoryStatuses;
import com.mkt.reward.domain.CostModes;
import com.mkt.reward.domain.FulfillmentModes;
import com.mkt.reward.domain.ReconPolicies;
import com.mkt.reward.domain.RewardTargets;
import com.mkt.reward.entity.PrizeCategoryEntity;
import java.time.LocalDateTime;

public final class CategoryFixtures {

    private CategoryFixtures() {}

    public static PrizeCategoryEntity points() {
        return category(
                BuiltinCategories.POINTS,
                "积分",
                RewardTargets.PLATFORM,
                FulfillmentModes.INSTANT,
                CostModes.NONE,
                0,
                null,
                "{\"points\":\"正整数\"}");
    }

    public static PrizeCategoryEntity alipay() {
        return category(
                BuiltinCategories.ALIPAY_RED,
                "支付宝红包",
                RewardTargets.THIRD_PARTY,
                FulfillmentModes.ASYNC,
                CostModes.FACE_VALUE,
                1,
                "alipay-red",
                "{\"faceFen\":\"正整数\"}");
    }

    public static PrizeCategoryEntity physical() {
        return category(
                BuiltinCategories.PHYSICAL,
                "实物",
                RewardTargets.PLATFORM,
                FulfillmentModes.ASYNC,
                CostModes.FIXED_UNIT,
                0,
                null,
                null);
    }

    public static PrizeCategoryEntity coupon() {
        return category(
                BuiltinCategories.COUPON,
                "优惠券",
                RewardTargets.PLATFORM,
                FulfillmentModes.INSTANT,
                CostModes.NONE,
                0,
                null,
                null);
    }

    public static PrizeCategoryEntity category(
            String code,
            String name,
            String target,
            String fulfill,
            String cost,
            int recon,
            String adapter,
            String schema) {
        PrizeCategoryEntity entity = new PrizeCategoryEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setRewardTarget(target);
        entity.setFulfillmentMode(fulfill);
        entity.setCostMode(cost);
        entity.setReconRequired(recon);
        entity.setReconActionPolicy(ReconPolicies.REVIEW);
        entity.setAdapterCode(adapter);
        entity.setParamSchema(schema);
        entity.setBuiltin(BuiltinCategories.isBuiltin(code) ? 1 : 0);
        entity.setStatus(CategoryStatuses.ENABLED);
        entity.setCreatedAt(LocalDateTime.parse("2026-08-19T00:00:00"));
        entity.setUpdatedAt(entity.getCreatedAt());
        return entity;
    }
}
