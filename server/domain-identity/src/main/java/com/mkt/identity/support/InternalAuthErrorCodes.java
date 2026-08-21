package com.mkt.identity.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** HMAC / nonce / app lookup codes for /internal/** (design §4.8). */
public enum InternalAuthErrorCodes implements ErrorCode {
    INVALID_SIGNATURE("internal.sign.invalid-signature", 401, "签名无效"),
    APP_NOT_FOUND("internal.app.not-found", 401, "调用方不存在"),
    APP_DISABLED("internal.app.disabled", 401, "调用方已失效"),
    NONCE_REPLAYED("internal.nonce.replayed", 400, "请求重复"),
    TIMESTAMP_SKEW("internal.timestamp.skew-exceeded", 400, "时间戳超出容差");

    private final String code;
    private final int httpStatus;
    private final String message;

    InternalAuthErrorCodes(String code, int httpStatus, String message) {
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
