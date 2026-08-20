package com.mkt.reward.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RewardRuntimeSettingsTest {

    @Test
    void autoRefulfillDefaultsOff() {
        RewardRuntimeSettings settings = new RewardRuntimeSettings();
        assertThat(settings.autoRefulfillEnabled()).isFalse();
        assertThat(settings.claimRetryMax()).isEqualTo(3);
        assertThat(settings.claimingTimeoutSeconds()).isEqualTo(30);
        assertThat(settings.sendingTimeoutHours()).isEqualTo(24);
    }
}
