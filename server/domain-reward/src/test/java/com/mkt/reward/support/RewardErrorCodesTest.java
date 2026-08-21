package com.mkt.reward.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.ErrorCodeFormat;
import org.junit.jupiter.api.Test;

class RewardErrorCodesTest {

    @Test
    void allCodesAreValid() {
        for (RewardErrorCodes code : RewardErrorCodes.values()) {
            assertThat(ErrorCodeFormat.isValid(code.code())).isTrue();
            assertThat(code.httpStatus()).isIn(400, 403, 404);
            assertThat(code.message()).isNotBlank();
        }
    }
}
