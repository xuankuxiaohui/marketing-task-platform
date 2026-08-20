package com.mkt.kernel;

/** User-visible business rejection (docs/standards/09). */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null, null);
    }

    public BusinessException(ErrorCode errorCode, String messageOverride) {
        this(errorCode, messageOverride, null, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.message(), null, cause);
    }

    public BusinessException(ErrorCode errorCode, String messageOverride, Throwable cause) {
        this(errorCode, messageOverride, null, cause);
    }

    public BusinessException(ErrorCode errorCode, String messageOverride, Object data) {
        this(errorCode, messageOverride, data, null);
    }

    public BusinessException(ErrorCode errorCode, String messageOverride, Object data, Throwable cause) {
        super(messageOverride, cause);
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object data() {
        return data;
    }
}
