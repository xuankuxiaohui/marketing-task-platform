package com.mkt.reward.domain;

import com.mkt.kernel.BusinessException;
import com.mkt.reward.support.RewardErrorCodes;
import java.util.Map;

/** Category-driven validation of unitCostFen / faceFen / points / adapter (R17.1 / R17.9). */
public final class PrizeParamRules {

    private PrizeParamRules() {}

    public static void requireValid(
            String categoryCode,
            String costMode,
            String rewardTarget,
            String categoryAdapter,
            Integer unitCostFen,
            Map<String, Object> typeParams) {
        if (BuiltinCategories.POINTS.equals(categoryCode)
                && TypeParams.positiveInt(typeParams, TypeParams.POINTS) == null) {
            throw new BusinessException(RewardErrorCodes.PRIZE_POINTS_AMOUNT_REQUIRED);
        }
        if (CostModes.FIXED_UNIT.equals(costMode) && (unitCostFen == null || unitCostFen <= 0)) {
            throw new BusinessException(RewardErrorCodes.PRIZE_COST_REQUIRED);
        }
        if (CostModes.FACE_VALUE.equals(costMode)
                && TypeParams.positiveInt(typeParams, TypeParams.FACE_FEN) == null) {
            throw new BusinessException(RewardErrorCodes.PRIZE_FACE_REQUIRED);
        }
        if (RewardTargets.THIRD_PARTY.equals(rewardTarget)) {
            String override = TypeParams.text(typeParams, TypeParams.ADAPTER_CODE);
            if ((override == null || override.isBlank())
                    && (categoryAdapter == null || categoryAdapter.isBlank())) {
                throw new BusinessException(RewardErrorCodes.PRIZE_ADAPTER_REQUIRED);
            }
        }
    }
}
