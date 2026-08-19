package com.mkt.kernel;

/** Session 401 codes (design §4.1 / D-02 / R1.13 / R4.3). */
public enum SessionErrorCodes implements ErrorCode {
    INVALID("auth.session.invalid", 401, "会话无效"),
    MISSING("auth.session.missing", 401, "请先登录"),
    EXPIRED("auth.session.expired", 401, "会话已过期"),
    KICKED_CONCURRENT("auth.session.kicked-concurrent", 401, "账号已在其他设备登录"),
    KICKED_ADMIN("auth.session.kicked-admin", 401, "账号已被下线，请联系客服");

    private final String code;
    private final int httpStatus;
    private final String message;

    SessionErrorCodes(String code, int httpStatus, String message) {
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
