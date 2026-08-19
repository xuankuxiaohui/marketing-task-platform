package com.mkt.task.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.ErrorCodeFormat;
import org.junit.jupiter.api.Test;

class TaskErrorCodesTest {

    @Test
    void allCodesAreValid() {
        for (TaskErrorCodes code : TaskErrorCodes.values()) {
            assertThat(ErrorCodeFormat.isValid(code.code())).isTrue();
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.message()).isNotBlank();
        }
    }
}
