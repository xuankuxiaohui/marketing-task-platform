package com.mkt.identity.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthErrorCodesTest {

    @Test
    void registeredCodesMatchDesign() {
        assertThat(AuthErrorCodes.LOGIN_INVALID_CREDENTIAL.code()).isEqualTo("auth.login.invalid-credential");
        assertThat(AuthErrorCodes.LOGIN_LOCKED.httpStatus()).isEqualTo(423);
        assertThat(AuthErrorCodes.CAPTCHA_EXPIRED.code()).isEqualTo("auth.captcha.expired");
        assertThat(AuthErrorCodes.RISK_BLOCKED_REGISTER.httpStatus()).isEqualTo(403);
        assertThat(AuthErrorCodes.RISK_BLOCKED_LOGIN.message()).isEqualTo("暂时无法登录");
        assertThat(AuthErrorCodes.ACCOUNT_DISABLED.httpStatus()).isEqualTo(403);
        assertThat(AuthErrorCodes.ROLE_BUILT_IN.code()).isEqualTo("auth.role.built-in");
        assertThat(AuthErrorCodes.ROLE_BUILT_IN.httpStatus()).isEqualTo(400);
        assertThat(AuthErrorCodes.USER_SELF_PROTECTED.code()).isEqualTo("auth.user.self-protected");
        assertThat(InternalAppErrorCodes.DUPLICATE.code()).isEqualTo("internal.app.duplicate");
        assertThat(InternalAuthErrorCodes.INVALID_SIGNATURE.code()).isEqualTo("internal.sign.invalid-signature");
        assertThat(InternalAuthErrorCodes.APP_NOT_FOUND.httpStatus()).isEqualTo(401);
        assertThat(InternalAuthErrorCodes.APP_DISABLED.code()).isEqualTo("internal.app.disabled");
        assertThat(InternalAuthErrorCodes.NONCE_REPLAYED.httpStatus()).isEqualTo(400);
        assertThat(InternalAuthErrorCodes.TIMESTAMP_SKEW.code()).isEqualTo("internal.timestamp.skew-exceeded");
        assertThat(SystemErrorCodes.DICT_ENTRY_DUPLICATE_VALUE.code()).isEqualTo("dict.entry.duplicate-value");
        assertThat(SystemErrorCodes.CONFIG_TYPE_MISMATCH.code()).isEqualTo("config.value.type-mismatch");
        assertThat(SystemErrorCodes.CACHE_NAMESPACE_UNKNOWN.code()).isEqualTo("cache.namespace.unknown");
    }
}
