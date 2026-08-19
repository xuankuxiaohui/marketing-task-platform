package com.mkt.identity.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Internal-app management codes (design §4.2). */
public enum InternalAppErrorCodes implements ErrorCode {
    DUPLICATE("internal.app.duplicate", 400, "调用方已存在");

    private final String code;
    private final int httpStatus;
    private final String message;

    InternalAppErrorCodes(String code, int httpStatus, String message) {
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
