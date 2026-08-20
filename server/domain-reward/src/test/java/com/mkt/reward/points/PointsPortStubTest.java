package com.mkt.reward.points;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PointsPortStubTest {

    @Test
    void earnReturnsZeroUntilTask35() {
        PointsPort stub = new PointsPortStub();
        assertThat(stub.earn(1L, 10, Instant.EPOCH, "TASK_STEP", "s-1")).isZero();
    }
}
