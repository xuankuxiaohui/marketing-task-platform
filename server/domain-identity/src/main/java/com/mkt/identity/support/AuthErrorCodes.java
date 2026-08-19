package com.mkt.identity.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Auth error codes (design §4.2 / §4.9.1). */
public enum AuthErrorCodes implements ErrorCode {
    CAPTCHA_INVALID("auth.captcha.invalid", 400, "验证码错误"),
    CAPTCHA_EXPIRED("auth.captcha.expired", 400, "验证码已过期"),
    LOGIN_INVALID_CREDENTIAL("auth.login.invalid-credential", 401, "用户名或密码错误"),
    LOGIN_LOCKED("auth.login.locked", 423, "账号已锁定，请稍后重试"),
    LOGIN_RATE_LIMITED("auth.login.rate-limited", 429, "登录过于频繁，请稍后重试"),
    REGISTER_RATE_LIMITED("auth.register.rate-limited", 429, "注册过于频繁，请稍后重试"),
    USERNAME_INVALID_FORMAT("auth.username.invalid-format", 400, "用户名格式不正确"),
    USERNAME_DUPLICATE("auth.username.duplicate", 400, "用户名已存在"),
    PASSWORD_POLICY_VIOLATED("auth.password.policy-violated", 400, "密码不符合复杂度要求"),
    PASSWORD_OLD_MISMATCH("auth.password.old-mismatch", 400, "原密码不正确"),
    ACCOUNT_DISABLED("auth.account.disabled", 403, "账号已停用"),
    ROLE_BUILT_IN("auth.role.built-in", 400, "内置角色不可删除或修改权限集"),
    RISK_BLOCKED_REGISTER("risk.blocked.register", 403, "暂时无法完成注册"),
    RISK_BLOCKED_LOGIN("risk.blocked.login", 403, "暂时无法登录");

    private final String code;
    private final int httpStatus;
    private final String message;

    AuthErrorCodes(String code, int httpStatus, String message) {
        ErrorCodeFormat.requireValid(code);
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String message() {
        return message;
    }
}
