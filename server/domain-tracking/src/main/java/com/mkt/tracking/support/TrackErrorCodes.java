package com.mkt.tracking.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Track error codes (design §3.9 / §4.9.3). */
public enum TrackErrorCodes implements ErrorCode {
    BATCH_OVERFLOW("track.batch.overflow", 400, "单批事件数超过上限"),
    BATCH_RATE_LIMITED("track.batch.rate-limited", 429, "上报过于频繁");

    private final String code;
    private final int httpStatus;
    private final String message;

    TrackErrorCodes(String code, int httpStatus, String message) {
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
