package com.mkt.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheErrorCodesTest {

    @Test
    void sessionForbiddenIsSpecifiedCode() {
        assertThat(CacheErrorCodes.SESSION_FORBIDDEN.code()).isEqualTo("system.cache.session-forbidden");
        assertThat(CacheErrorCodes.SESSION_FORBIDDEN.httpStatus()).isEqualTo(400);
        assertThat(CacheErrorCodes.SESSION_FORBIDDEN.message()).isNotBlank();
    }
}
