package com.mkt.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiskCntKeysTest {

    @Test
    void formatMatchesSection310() {
        assertThat(RiskCntKeys.of(RiskRuleCode.RA, RiskCntKeys.DIM_USER, "9"))
                .isEqualTo("risk:cnt:R-a:USER:9");
        assertThat(RiskCntKeys.of(RiskRuleCode.RF, RiskCntKeys.DIM_IP, "10.0.0.1"))
                .isEqualTo("risk:cnt:R-f:IP:10.0.0.1");
    }
}
