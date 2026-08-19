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
    }
}
