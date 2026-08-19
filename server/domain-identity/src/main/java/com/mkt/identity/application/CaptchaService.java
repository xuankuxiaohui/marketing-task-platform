package com.mkt.identity.application;

import com.mkt.identity.config.ConfigService;
import com.mkt.identity.response.CaptchaResponse;
import com.mkt.identity.support.AuthConfigKeys;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.redis.KeyValueStore;
import com.mkt.kernel.BusinessException;
import com.wf.captcha.SpecCaptcha;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private final KeyValueStore store;
    private final ConfigService configs;

    public CaptchaService(KeyValueStore store, ConfigService configs) {
        this.store = store;
        this.configs = configs;
    }

    public CaptchaResponse issue(String realm) {
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        String id = UUID.randomUUID().toString();
        int ttl = configs.getInt(AuthConfigKeys.CAPTCHA_TTL_SECONDS, AuthConfigKeys.DEFAULT_CAPTCHA_TTL);
        store.set(key(realm, id), captcha.text().toLowerCase(Locale.ROOT), Duration.ofSeconds(ttl));
        return new CaptchaResponse(id, captcha.toBase64());
    }

    public void consume(String realm, String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank()) {
            throw new BusinessException(AuthErrorCodes.CAPTCHA_INVALID);
        }
        String redisKey = key(realm, captchaId);
        String expected = store.get(redisKey);
        store.unlink(redisKey);
        if (expected == null) {
            throw new BusinessException(AuthErrorCodes.CAPTCHA_EXPIRED);
        }
        String actual = captchaCode == null ? "" : captchaCode.trim().toLowerCase(Locale.ROOT);
        if (!expected.equals(actual)) {
            throw new BusinessException(AuthErrorCodes.CAPTCHA_INVALID);
        }
    }

    public static String key(String realm, String id) {
        return "captcha:" + realm + ":" + id;
    }
}
