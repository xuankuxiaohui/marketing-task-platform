package com.marketing.task.security;

import com.marketing.task.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

    private final CaptchaService captchaService = new CaptchaService();

    @Test
    void generate_shouldReturnValidCaptchaResult() {
        CaptchaService.CaptchaResult result = captchaService.generate();

        assertNotNull(result);
        assertNotNull(result.captchaKey());
        assertFalse(result.captchaKey().isBlank());
        assertNotNull(result.captchaImage());
        assertTrue(result.captchaImage().startsWith("data:image"));
    }

    @Test
    void verify_shouldThrowBusinessException_whenCodeIsEmpty() {
        CaptchaService.CaptchaResult result = captchaService.generate();

        assertThrows(BusinessException.class, () ->
                captchaService.verify(result.captchaKey(), ""));
    }

    @Test
    void verify_shouldThrowBusinessException_whenCodeWrong() {
        CaptchaService.CaptchaResult result = captchaService.generate();

        assertThrows(BusinessException.class, () ->
                captchaService.verify(result.captchaKey(), "xyzw"));
    }

    @Test
    void verify_shouldThrowBusinessException_whenKeyNotFound() {
        assertThrows(BusinessException.class, () ->
                captchaService.verify("nonexistent-key", "abcd"));
    }

    @Test
    void verify_shouldThrowBusinessException_whenKeyIsNull() {
        assertThrows(BusinessException.class, () ->
                captchaService.verify(null, "abcd"));
    }

    @Test
    void verify_shouldThrowBusinessException_whenCodeIsNull() {
        CaptchaService.CaptchaResult result = captchaService.generate();

        assertThrows(BusinessException.class, () ->
                captchaService.verify(result.captchaKey(), null));
    }
}
