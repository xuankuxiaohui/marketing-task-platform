package com.mkt.kernel;

/** 429 with {@code Retry-After} (docs/standards/09 §4). */
public class RateLimitedException extends BusinessException {

    private final int retryAfterSeconds;

    public RateLimitedException(ErrorCode errorCode, int retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
