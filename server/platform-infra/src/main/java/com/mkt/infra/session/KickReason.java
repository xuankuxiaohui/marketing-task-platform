package com.mkt.infra.session;

/** D-02 portal kick codes written to {@code session:kick-reason:{loginType}:{token}}. */
public enum KickReason {
    CONCURRENT("auth.session.kicked-concurrent"),
    ADMIN("auth.session.kicked-admin");

    private final String code;

    KickReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static KickReason fromCode(String code) {
        for (KickReason reason : values()) {
            if (reason.code.equals(code)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("unknown kick reason: " + code);
    }
}
