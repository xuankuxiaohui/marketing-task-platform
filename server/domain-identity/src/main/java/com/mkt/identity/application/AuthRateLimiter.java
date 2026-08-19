package com.mkt.identity.application;

import com.mkt.identity.config.ConfigService;
import com.mkt.identity.support.AuthConfigKeys;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.RateLimitedException;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {

    public static final int WINDOW_SECONDS = 60;

    private final SlidingWindowRateLimiter limiter;
    private final ConfigService configs;

    public AuthRateLimiter(SlidingWindowRateLimiter limiter, ConfigService configs) {
        this.limiter = limiter;
        this.configs = configs;
    }

    public void assertLogin(String ip, String account) {
        assertBuckets(ip, account, AuthErrorCodes.LOGIN_RATE_LIMITED);
    }

    public void assertRegister(String ip, String account) {
        assertBuckets(ip, account, AuthErrorCodes.REGISTER_RATE_LIMITED);
    }

    public void assertIpOnly(String ip, ErrorCode code) {
        int ipMax = configs.getInt(AuthConfigKeys.LOGIN_IP_PER_MINUTE, AuthConfigKeys.DEFAULT_LOGIN_IP_PER_MINUTE);
        if (!limiter.tryAcquire(RateLimitDim.IP, ip == null ? "unknown" : ip, WINDOW_SECONDS, ipMax)) {
            throw new RateLimitedException(code, WINDOW_SECONDS);
        }
    }

    private void assertBuckets(String ip, String account, ErrorCode code) {
        int ipMax = configs.getInt(AuthConfigKeys.LOGIN_IP_PER_MINUTE, AuthConfigKeys.DEFAULT_LOGIN_IP_PER_MINUTE);
        int accountMax =
                configs.getInt(AuthConfigKeys.LOGIN_ACCOUNT_PER_MINUTE, AuthConfigKeys.DEFAULT_LOGIN_ACCOUNT_PER_MINUTE);
        if (!limiter.tryAcquireLogin(
                ip == null ? "unknown" : ip,
                account == null || account.isBlank() ? "unknown" : account,
                WINDOW_SECONDS,
                ipMax,
                WINDOW_SECONDS,
                accountMax)) {
            throw new RateLimitedException(code, WINDOW_SECONDS);
        }
    }
}
