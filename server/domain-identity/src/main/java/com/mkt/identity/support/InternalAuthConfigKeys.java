package com.mkt.identity.support;

/** Appendix A keys for internal HMAC (R15.2 / R15.5). */
public final class InternalAuthConfigKeys {

    public static final String TIMESTAMP_TOLERANCE_SECONDS = "internal.timestamp.tolerance-seconds";
    public static final String NONCE_TTL_SECONDS = "internal.nonce.ttl-seconds";
    public static final String APP_RATE_PER_SECOND = "ratelimit.internal.accesskey.per-second";

    public static final int DEFAULT_TIMESTAMP_TOLERANCE_SECONDS = 300;
    public static final int DEFAULT_NONCE_TTL_SECONDS = 600;
    public static final int DEFAULT_APP_RATE_PER_SECOND = 200;
    public static final int MAX_NONCE_LENGTH = 64;
    public static final int HMAC_HEX_LENGTH = 64;

    public static final String HEADER_APP_ID = "X-App-Id";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGN = "X-Sign";

    private InternalAuthConfigKeys() {}
}
