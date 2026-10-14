package com.mkt.spike.hutool;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HutoolSmokeTest {

    @Test
    void hutoolCoreCoexistsWithToolsJackson() {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("mobile", "13812345678");
        String json = JsonMapper.shared().writeValueAsString(payload);
        assertTrue(json.contains("13812345678"));
        assertFalse(json.contains("hutool"));
    }

    @Test
    void desensitizeMobileAndPassword() {
        assertEquals("138****5678", DesensitizedUtil.mobilePhone("13812345678"));
        String hidden = DesensitizedUtil.password("Abcdef12!@");
        assertFalse(hidden.contains("Abc"));
        assertTrue(hidden.contains("*"));
    }

    @Test
    void hmacSha256LowerHex() {
        byte[] key = "secret".getBytes(StandardCharsets.UTF_8);
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, key);
        String hex = hmac.digestHex("hello", StandardCharsets.UTF_8);
        assertEquals(64, hex.length());
        assertEquals(hex, hex.toLowerCase());
        assertEquals(hex, SecureUtil.hmacSha256("secret").digestHex("hello"));
    }
}
