package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import org.junit.jupiter.api.Test;

class CaptchaServiceTest {

    private final MemoryKeyValueStore kv = new MemoryKeyValueStore();
    private final CaptchaService captchas = new CaptchaService(kv, (key, def) -> 120);

    @Test
    void issueThenConsumeOnce() {
        var issued = captchas.issue("admin");
        assertThat(issued.captchaId()).isNotBlank();
        assertThat(issued.imageBase64()).isNotBlank();
        String stored = kv.get(CaptchaService.key("admin", issued.captchaId()));
        captchas.consume("admin", issued.captchaId(), stored);
        assertThatThrownBy(() -> captchas.consume("admin", issued.captchaId(), stored))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.CAPTCHA_EXPIRED);
    }

    @Test
    void mismatchIsInvalid() {
        var issued = captchas.issue("portal");
        assertThatThrownBy(() -> captchas.consume("portal", issued.captchaId(), "xxxx"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AuthErrorCodes.CAPTCHA_INVALID);
    }
}
