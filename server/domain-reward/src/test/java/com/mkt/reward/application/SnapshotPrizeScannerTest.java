package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.json.JsonUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotPrizeScannerTest {

    @Test
    void detectsRewardPrizeIdInSnapshotJson() {
        String content = JsonUtil.toJson(Map.of(
                "steps",
                List.of(
                        Map.of("code", "s1", "type", "CLICK", "seq", 1),
                        Map.of("code", "r1", "type", "REWARD", "seq", 2, "prizeId", 42))));
        assertThat(SnapshotPrizeScanner.contentReferencesPrize(content, 42)).isTrue();
        assertThat(SnapshotPrizeScanner.contentReferencesPrize(content, 7)).isFalse();
        assertThat(SnapshotPrizeScanner.rewardStepCodes(content, 42)).containsExactly("r1");
    }
}
