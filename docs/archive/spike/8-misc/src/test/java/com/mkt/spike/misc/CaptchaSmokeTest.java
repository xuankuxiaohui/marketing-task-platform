package com.mkt.spike.misc;

import com.wf.captcha.SpecCaptcha;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaSmokeTest {

    @Test
    void generateAndOneShotVerify() {
        SpecCaptcha captcha = new SpecCaptcha(120, 40, 4);
        String code = captcha.text();
        assertEquals(4, code.length());
        assertTrue(captcha.toBase64().startsWith("data:image"));
        assertTrue(code.equalsIgnoreCase(code));
        assertFalse(code.isBlank());
        SpecCaptcha next = new SpecCaptcha(120, 40, 4);
        assertNotEquals(code, next.text());
    }
}
