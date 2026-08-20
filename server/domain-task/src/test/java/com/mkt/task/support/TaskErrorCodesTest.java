package com.mkt.task.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.ErrorCodeFormat;
import org.junit.jupiter.api.Test;

class TaskErrorCodesTest {

    @Test
    void allCodesAreValid() {
        for (TaskErrorCodes code : TaskErrorCodes.values()) {
            assertThat(ErrorCodeFormat.isValid(code.code())).isTrue();
            assertThat(code.httpStatus()).isIn(400, 403, 404, 429);
            assertThat(code.message()).isNotBlank();
        }
        assertThat(TaskErrorCodes.CLAIM_NOT_VISIBLE.httpStatus()).isEqualTo(400);
        assertThat(TaskErrorCodes.ACCOUNT_DISABLED.httpStatus()).isEqualTo(403);
        assertThat(TaskErrorCodes.RISK_BLOCKED_GENERIC.httpStatus()).isEqualTo(403);
        assertThat(TaskErrorCodes.CLAIM_RATE_LIMITED.httpStatus()).isEqualTo(429);
        assertThat(TaskErrorCodes.INSTANCE_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(TaskErrorCodes.STEP_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(TaskErrorCodes.STEP_STATE_MISMATCH.httpStatus()).isEqualTo(400);
        assertThat(TaskErrorCodes.PROGRESS_PROCESSING.httpStatus()).isEqualTo(400);
    }
}
