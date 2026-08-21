package com.mkt.reward.domain;

import java.util.Map;

/** Cost algorithm from category + prize fields (R17.9). */
public final class PrizeCosts {

    private PrizeCosts() {}

    public static PrizeCost snapshot(String costMode, Integer unitCostFen, Map<String, Object> typeParams) {
        if (CostModes.NONE.equals(costMode) || costMode == null) {
            return PrizeCost.none();
        }
        if (CostModes.FIXED_UNIT.equals(costMode)) {
            int cost = unitCostFen == null ? 0 : unitCostFen;
            return new PrizeCost(cost, null);
        }
        if (CostModes.FACE_VALUE.equals(costMode)) {
            Integer face = TypeParams.positiveInt(typeParams, TypeParams.FACE_FEN);
            int fen = face == null ? 0 : face;
            return new PrizeCost(fen, fen);
        }
        return PrizeCost.none();
    }
}
