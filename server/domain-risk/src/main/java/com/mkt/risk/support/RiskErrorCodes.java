package com.mkt.risk.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Risk error codes (design §3.9 / §4.6). */
public enum RiskErrorCodes implements ErrorCode {
    LIST_DUPLICATE_RETURNED("risk.list.duplicate-returned", 400, "名单条目已存在"),
    RULE_RANGE_VIOLATED("risk.rule.range-violated", 400, "规则阈值或时间窗口超出合法范围");

    private final String code;
    private final int httpStatus;
    private final String message;

    RiskErrorCodes(String code, int httpStatus, String message) {
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
