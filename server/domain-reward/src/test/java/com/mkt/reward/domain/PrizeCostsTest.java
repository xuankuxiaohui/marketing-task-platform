package com.mkt.reward.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PrizeCostsTest {

    @Test
    void noneIsZero() {
        assertThat(PrizeCosts.snapshot(CostModes.NONE, 99, Map.of("points", 10)))
                .isEqualTo(PrizeCost.none());
    }

    @Test
    void fixedUnitUsesUnitCost() {
        assertThat(PrizeCosts.snapshot(CostModes.FIXED_UNIT, 350, Map.of()))
                .isEqualTo(new PrizeCost(350, null));
    }

    @Test
    void faceValueCopiesFaceFen() {
        assertThat(PrizeCosts.snapshot(CostModes.FACE_VALUE, null, Map.of("faceFen", 88)))
                .isEqualTo(new PrizeCost(88, 88));
    }
}
