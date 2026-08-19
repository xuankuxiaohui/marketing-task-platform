package com.mkt.contract;

/**
 * Permanent grant failure. Reason is the R14.5 closed enum, not an HTTP ErrorCode
 * (those are registered when domain-reward implements grant).
 */
public class PermanentGrantException extends RuntimeException {

    private final PermanentGrantReason reason;

    public PermanentGrantException(PermanentGrantReason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public PermanentGrantReason reason() {
        return reason;
    }
}
