package com.mkt.activity.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Activity error codes (design §3.9 scene {@code activity}). */
public enum ActivityErrorCodes implements ErrorCode {
    NOT_FOUND("activity.activity.not-found", 404, "活动不存在"),
    NOT_PUBLISHED("activity.activity.not-published", 400, "活动未开始"),
    OUT_OF_WINDOW("activity.activity.out-of-window", 400, "不在活动时间内"),
    CODE_DUPLICATE("activity.activity.code-duplicate", 400, "活动编码已存在"),
    TIME_WINDOW_INVALID("activity.activity.time-window-invalid", 400, "时间窗无效"),
    PUBLISHED_NOT_DELETABLE("activity.activity.published-not-deletable", 400, "已发布活动不可删除"),
    PRIZE_INVALID("activity.activity.prize-invalid", 400, "参与奖品未启用"),
    HTML_EMPTY("activity.activity.html-empty", 400, "富文本内容无效"),
    HTML_TOO_LONG("activity.activity.html-too-long", 400, "富文本内容过长"),
    GRAY_INVALID("activity.activity.gray-invalid", 400, "灰度配置无效"),
    SUBMODULE_INVALID("activity.activity.submodule-invalid", 400, "关联子模块无效"),
    RULE_INVALID("activity.activity.rule-invalid", 400, "参与规则配置无效"),
    RATE_LIMITED("activity.activity.rate-limited", 429, "操作过于频繁，请稍后重试");

    private final String code;
    private final int httpStatus;
    private final String message;

    ActivityErrorCodes(String code, int httpStatus, String message) {
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
