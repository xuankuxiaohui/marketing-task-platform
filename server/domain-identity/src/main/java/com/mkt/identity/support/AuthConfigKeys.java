package com.mkt.identity.support;

/** Appendix A keys used by task 21. */
public final class AuthConfigKeys {

    public static final String CAPTCHA_TTL_SECONDS = "auth.captcha.ttl-seconds";
    public static final String ADMIN_MAX_CONCURRENT = "auth.admin.session.max-concurrent";
    public static final String PORTAL_MAX_CONCURRENT = "auth.portal.session.max-concurrent";
    public static final String LOGIN_IP_PER_MINUTE = "ratelimit.login.ip.per-minute";
    public static final String LOGIN_ACCOUNT_PER_MINUTE = "ratelimit.login.account.per-minute";

    public static final int DEFAULT_CAPTCHA_TTL = 120;
    public static final int DEFAULT_ADMIN_MAX_CONCURRENT = 5;
    public static final int DEFAULT_PORTAL_MAX_CONCURRENT = 3;
    public static final int DEFAULT_LOGIN_IP_PER_MINUTE = 20;
    public static final int DEFAULT_LOGIN_ACCOUNT_PER_MINUTE = 10;

    private AuthConfigKeys() {
    }
}
