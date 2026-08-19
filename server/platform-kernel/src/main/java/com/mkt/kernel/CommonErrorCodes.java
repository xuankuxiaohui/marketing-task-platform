package com.mkt.kernel;

/** Closed public codes (design §3.9 / docs/standards/09). */
public enum CommonErrorCodes implements ErrorCode {
    PARAM_INVALID("common.param-invalid", 400, "参数不合法"),
    PERMISSION_DENIED("common.permission-denied", 403, "无权限"),
    NOT_FOUND("common.not-found", 404, "资源不存在"),
    RATE_LIMITED("common.rate-limited", 429, "请求过于频繁"),
    SERVER_ERROR("common.server-error", 500, "服务异常，请稍后重试");

    private final String code;
    private final int httpStatus;
    private final String message;

    CommonErrorCodes(String code, int httpStatus, String message) {
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
