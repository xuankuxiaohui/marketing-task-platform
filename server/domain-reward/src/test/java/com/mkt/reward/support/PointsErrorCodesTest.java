package com.mkt.reward.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.ErrorCodeFormat;
import org.junit.jupiter.api.Test;

class PointsErrorCodesTest {

    @Test
    void allCodesAreValid() {
        for (PointsErrorCodes code : PointsErrorCodes.values()) {
            assertThat(ErrorCodeFormat.isValid(code.code())).isTrue();
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.message()).isNotBlank();
        }
    }
}
