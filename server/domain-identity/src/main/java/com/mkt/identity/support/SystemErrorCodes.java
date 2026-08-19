package com.mkt.identity.support;

import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.ErrorCodeFormat;

/** Dict / config / cache codes (design §4.3 / §3.9). */
public enum SystemErrorCodes implements ErrorCode {
    DICT_DUPLICATE_CODE("dict.dict.duplicate-code", 400, "字典类型编码已存在"),
    DICT_ENTRY_DUPLICATE_VALUE("dict.entry.duplicate-value", 400, "字典项键值已存在"),
    CONFIG_TYPE_MISMATCH("config.value.type-mismatch", 400, "配置值与类型不匹配"),
    CACHE_NAMESPACE_UNKNOWN("cache.namespace.unknown", 400, "未知缓存命名空间");

    private final String code;
    private final int httpStatus;
    private final String message;

    SystemErrorCodes(String code, int httpStatus, String message) {
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
