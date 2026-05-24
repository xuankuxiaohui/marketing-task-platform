package com.marketing.task.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    private final int code;
    private final String message;
    private final String subCode;
    private final T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", null, data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, errorCode.getSubCode(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getDefaultMessage(), errorCode.getSubCode(), null);
    }
}
