package com.mkt.contract;

/**
 * Retryable grant failure. Caller rolls back the step cascade (design §2.2.3 / R14.5).
 */
public class RetryableGrantException extends RuntimeException {

    private final RetryableGrantReason reason;

    public RetryableGrantException(RetryableGrantReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public RetryableGrantReason reason() {
        return reason;
    }
}
