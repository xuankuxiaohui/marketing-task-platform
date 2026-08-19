package com.mkt.infra.cache;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** R9.2 / docs/standards/05-security.md. */
public enum CacheErrorCodes implements ErrorCode {
    SESSION_FORBIDDEN("system.cache.session-forbidden", 400, "禁止清理会话缓存");

    private final String code;
    private final int httpStatus;
    private final String message;

    CacheErrorCodes(String code, int httpStatus, String message) {
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
