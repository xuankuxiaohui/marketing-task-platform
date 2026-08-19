package com.mkt.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionErrorCodesTest {

    @Test
    void closedFourPortalCodesAndAdminInvalid() {
        assertThat(SessionErrorCodes.INVALID.code()).isEqualTo("auth.session.invalid");
        assertThat(SessionErrorCodes.MISSING.code()).isEqualTo("auth.session.missing");
        assertThat(SessionErrorCodes.EXPIRED.code()).isEqualTo("auth.session.expired");
        assertThat(SessionErrorCodes.KICKED_CONCURRENT.code()).isEqualTo("auth.session.kicked-concurrent");
        assertThat(SessionErrorCodes.KICKED_ADMIN.code()).isEqualTo("auth.session.kicked-admin");
        assertThat(SessionErrorCodes.INVALID.httpStatus()).isEqualTo(401);
        assertThat(SessionErrorCodes.KICKED_CONCURRENT.message()).contains("其他设备");
        assertThat(SessionErrorCodes.KICKED_ADMIN.message()).contains("下线");
    }
}
