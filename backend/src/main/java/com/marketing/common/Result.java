package com.marketing.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private final int code;
    private final Integer httpStatus;
    private final String message;
    private final String subCode;
    private final T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, null, "ok", null, data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), errorCode.getHttpStatus(), message, errorCode.getSubCode(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getDefaultMessage());
    }
}
