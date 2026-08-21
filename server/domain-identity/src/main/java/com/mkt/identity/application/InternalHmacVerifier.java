package com.mkt.identity.application;

import com.mkt.identity.config.ConfigService;
import com.mkt.identity.domain.InternalHmacs;
import com.mkt.identity.entity.InternalAppEntity;
import com.mkt.identity.mapper.InternalAppMapper;
import com.mkt.identity.support.InternalAuthConfigKeys;
import com.mkt.identity.support.InternalAuthErrorCodes;
import com.mkt.infra.nonce.NonceStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** HMAC + nonce for {@code /internal/**} (R15.2). */
public final class InternalHmacVerifier {

    private final InternalAppMapper apps;
    private final InternalAppSecretCipher cipher;
    private final InternalAppSecretCache cache;
    private final NonceStore nonces;
    private final ConfigService configs;
    private final Clock clock;

    public InternalHmacVerifier(
            InternalAppMapper apps,
            InternalAppSecretCipher cipher,
            InternalAppSecretCache cache,
            NonceStore nonces,
            ConfigService configs,
            Clock clock) {
        this.apps = apps;
        this.cipher = cipher;
        this.cache = cache;
        this.nonces = nonces;
        this.configs = configs;
        this.clock = clock;
    }

    /**
     * @return appId when the request is authentic
     */
    public String authenticate(
            String method, String path, String appIdHeader, String timestampHeader, String nonceHeader, String signHeader, byte[] body) {
        String appId = blankToNull(appIdHeader);
        String timestamp = blankToNull(timestampHeader);
        String nonce = blankToNull(nonceHeader);
        String sign = blankToNull(signHeader);
        if (appId == null || timestamp == null || nonce == null) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (nonce.length() > InternalAuthConfigKeys.MAX_NONCE_LENGTH) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (sign == null
                || sign.length() != InternalAuthConfigKeys.HMAC_HEX_LENGTH
                || InternalHmacs.decodeHex(sign) == null) {
            throw new BusinessException(InternalAuthErrorCodes.INVALID_SIGNATURE);
        }
        assertTimestamp(timestamp);
        InternalAppEntity app = load(appId);
        if (app == null) {
            throw new BusinessException(InternalAuthErrorCodes.APP_NOT_FOUND);
        }
        if (!app.enabled()) {
            throw new BusinessException(InternalAuthErrorCodes.APP_DISABLED);
        }
        String stringToSign = InternalHmacs.stringToSign(method, path, timestamp, nonce, body);
        List<String> secrets = cipher.decryptActive(app, clock.instant());
        boolean match = false;
        for (String secret : secrets) {
            String expected = InternalHmacs.signHex(secret, stringToSign);
            if (InternalHmacs.equalsConstantTime(expected, sign)) {
                match = true;
            }
        }
        if (!match) {
            throw new BusinessException(InternalAuthErrorCodes.INVALID_SIGNATURE);
        }
        int ttl = configs.getInt(
                InternalAuthConfigKeys.NONCE_TTL_SECONDS, InternalAuthConfigKeys.DEFAULT_NONCE_TTL_SECONDS);
        if (!nonces.tryConsume(appId, nonce, ttl)) {
            throw new BusinessException(InternalAuthErrorCodes.NONCE_REPLAYED);
        }
        return appId;
    }

    private void assertTimestamp(String timestamp) {
        long millis;
        try {
            millis = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new BusinessException(InternalAuthErrorCodes.TIMESTAMP_SKEW);
        }
        int toleranceSeconds = configs.getInt(
                InternalAuthConfigKeys.TIMESTAMP_TOLERANCE_SECONDS,
                InternalAuthConfigKeys.DEFAULT_TIMESTAMP_TOLERANCE_SECONDS);
        long skew = Math.abs(clock.millis() - millis);
        if (skew > toleranceSeconds * 1000L) {
            throw new BusinessException(InternalAuthErrorCodes.TIMESTAMP_SKEW);
        }
    }

    private InternalAppEntity load(String appId) {
        Instant now = clock.instant();
        InternalAppEntity cached = cache == null ? null : cache.get(appId, now);
        if (cached != null) {
            return cached;
        }
        InternalAppEntity row = apps.getByAppId(appId);
        if (row != null && cache != null) {
            cache.put(appId, row, now);
        }
        return row;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
