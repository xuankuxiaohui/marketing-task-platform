package com.marketing.task.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    private final ErrorCode errorCode;

    public BusinessException(String message) {
        this(ErrorCode.BAD_REQUEST, message);
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }
}
