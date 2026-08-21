package com.mkt.signin.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Signin error codes (design §3.9 scene {@code signin}). */
public enum SigninErrorCodes implements ErrorCode {
    NOT_FOUND("signin.signin.not-found", 404, "签到活动不存在"),
    NOT_PUBLISHED("signin.signin.not-published", 400, "签到活动未开始"),
    OUT_OF_WINDOW("signin.signin.out-of-window", 400, "不在活动时间内"),
    DUPLICATE_DAY("signin.signin.duplicate-day", 400, "今日已签到"),
    CATCHUP_WINDOW("signin.signin.catchup-window", 400, "已超过补签期限"),
    CATCHUP_LIMIT("signin.signin.catchup-limit", 400, "今日补签次数已达上限"),
    CATCHUP_NOT_ALLOWED("signin.signin.catchup-not-allowed", 400, "该日期不可补签"),
    CODE_DUPLICATE("signin.signin.code-duplicate", 400, "活动编码已存在"),
    TIER_INVALID("signin.signin.tier-invalid", 400, "梯度配置无效"),
    TIME_WINDOW_INVALID("signin.signin.time-window-invalid", 400, "时间窗无效"),
    PUBLISHED_NOT_DELETABLE("signin.signin.published-not-deletable", 400, "已发布活动不可删除"),
    PRIZE_INVALID("signin.signin.prize-invalid", 400, "梯度奖品未启用"),
    RATE_LIMITED("signin.signin.rate-limited", 429, "操作过于频繁，请稍后重试");

    private final String code;
    private final int httpStatus;
    private final String message;

    SigninErrorCodes(String code, int httpStatus, String message) {
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
