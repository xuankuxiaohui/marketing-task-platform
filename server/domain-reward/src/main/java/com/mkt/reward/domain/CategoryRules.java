package com.mkt.reward.domain;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;

/** Cross-field checks for prize categories (design §3.4.0 / §3.4.1). */
public final class CategoryRules {

    private CategoryRules() {}

    public static void requireShape(
            String code, String rewardTarget, String fulfillmentMode, String costMode, String reconActionPolicy) {
        if (!RewardTargets.valid(rewardTarget)
                || !FulfillmentModes.valid(fulfillmentMode)
                || !CostModes.valid(costMode)
                || !ReconPolicies.valid(reconActionPolicy)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "分类枚举非法");
        }
        if (RewardTargets.THIRD_PARTY.equals(rewardTarget)
                && !FulfillmentModes.ASYNC.equals(fulfillmentMode)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "第三方分类履约必须为 ASYNC");
        }
        if (BuiltinCategories.POINTS.equals(code)
                && !(RewardTargets.PLATFORM.equals(rewardTarget)
                        && FulfillmentModes.INSTANT.equals(fulfillmentMode)
                        && CostModes.NONE.equals(costMode))) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "POINTS 分类必须为 PLATFORM/INSTANT/NONE");
        }
    }
}
