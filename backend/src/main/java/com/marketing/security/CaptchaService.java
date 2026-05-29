package com.marketing.security;

import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.wf.captcha.SpecCaptcha;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final long TTL_MS = 60_000L;
    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    public CaptchaService() {
        Thread cleanup = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30_000L);
                    long now = System.currentTimeMillis();
                    store.entrySet().removeIf(e -> e.getValue().expireAt() < now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "captcha-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    public CaptchaResult generate() {
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(com.wf.captcha.base.Captcha.TYPE_DEFAULT);
        String code = captcha.text().toLowerCase();
        String key = UUID.randomUUID().toString().replace("-", "");
        store.put(key, new CaptchaEntry(code, System.currentTimeMillis() + TTL_MS));
        return new CaptchaResult(key, captcha.toBase64());
    }

    public void verify(String key, String code) {
        if (key == null || code == null) {
            throw new BusinessException(ErrorCode.CAPTCHA_MISSING);
        }
        CaptchaEntry entry = store.remove(key);
        if (entry == null) {
            throw new BusinessException(ErrorCode.CAPTCHA_EXPIRED);
        }
        if (System.currentTimeMillis() > entry.expireAt()) {
            throw new BusinessException(ErrorCode.CAPTCHA_EXPIRED);
        }
        if (!entry.code().equalsIgnoreCase(code.trim())) {
            throw new BusinessException(ErrorCode.CAPTCHA_WRONG);
        }
    }

    public record CaptchaResult(String captchaKey, String captchaImage) {
    }

    private record CaptchaEntry(String code, long expireAt) {
    }
}
