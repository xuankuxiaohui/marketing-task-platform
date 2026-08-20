package com.mkt.reward.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Points error codes (design §3.9 / §4.5). */
public enum PointsErrorCodes implements ErrorCode {
    INSUFFICIENT_BALANCE("points.account.insufficient-balance", 400, "积分余额不足"),
    REASON_REQUIRED("points.account.reason-required", 400, "调整原因不能为空"),
    ACCOUNT_NOT_FOUND("points.account.not-found", 400, "积分账户不存在");

    private final String code;
    private final int httpStatus;
    private final String message;

    PointsErrorCodes(String code, int httpStatus, String message) {
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
