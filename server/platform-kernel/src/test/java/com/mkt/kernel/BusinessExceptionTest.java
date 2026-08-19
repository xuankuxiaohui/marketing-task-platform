package com.mkt.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void defaultMessageComesFromErrorCode() {
        BusinessException ex = new BusinessException(CommonErrorCodes.NOT_FOUND);
        assertThat(ex.errorCode()).isEqualTo(CommonErrorCodes.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo(CommonErrorCodes.NOT_FOUND.message());
    }

    @Test
    void messageOverrideDoesNotLeakCauseText() {
        RuntimeException cause = new RuntimeException("jdbc url leaked");
        BusinessException ex = new BusinessException(CommonErrorCodes.SERVER_ERROR, cause);
        assertThat(ex.getMessage()).isEqualTo(CommonErrorCodes.SERVER_ERROR.message());
        assertThat(ex.getCause()).isSameAs(cause);

        BusinessException overridden =
                new BusinessException(CommonErrorCodes.PARAM_INVALID, "page: must be >= 1", cause);
        assertThat(overridden.getMessage()).isEqualTo("page: must be >= 1");
    }
}
