package com.mkt.kernel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mkt.kernel.trace.TraceIds;

/**
 * Appendix C envelope. Success {@code code} is number {@code 0}; failure {@code code} is a string.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(Object code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data, TraceIds.current());
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null, TraceIds.current());
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.code(), errorCode.message(), null, TraceIds.current());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String messageOverride) {
        return new Result<>(errorCode.code(), messageOverride, null, TraceIds.current());
    }

    public boolean success() {
        return Integer.valueOf(0).equals(code);
    }
}
