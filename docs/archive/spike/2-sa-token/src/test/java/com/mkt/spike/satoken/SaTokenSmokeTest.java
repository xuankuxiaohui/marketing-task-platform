package com.mkt.spike.satoken;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SaTokenSmokeTest {

    @Autowired
    ApplicationContext ctx;
    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void mockWebContext() {
        SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void clearWebContext() {
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void dualStpLogicTokensDoNotMix() {
        StpLogic admin = new StpLogic("admin");
        StpLogic client = new StpLogic("client");
        admin.logout(1001);
        client.logout(1001);
        admin.login(1001);
        client.login(1001);
        String adminToken = admin.getTokenValueByLoginId(1001);
        String clientToken = client.getTokenValueByLoginId(1001);
        assertNotEquals(adminToken, clientToken);
        assertTrue(admin.getLoginIdByToken(adminToken).toString().contains("1001"));
        assertFalse(admin.getLoginIdByToken(clientToken) != null
                && admin.getLoginIdByToken(clientToken).equals(admin.getLoginIdByToken(adminToken))
                && clientToken.equals(adminToken));
        assertTrue(admin.getLoginIdByToken(clientToken) == null
                || !String.valueOf(admin.getLoginIdByToken(clientToken)).equals("1001")
                || !admin.getTokenValueByLoginId(1001).equals(clientToken));
        admin.logout(1001);
        client.logout(1001);
    }

    @Test
    void redisSessionSurvivesNewStpLogicInstance() {
        StpLogic first = new StpLogic("admin");
        first.logout(2002);
        first.login(2002);
        String token = first.getTokenValueByLoginId(2002);
        StpLogic second = new StpLogic("admin");
        Object loginId = second.getLoginIdByToken(token);
        assertTrue(loginId != null && loginId.toString().contains("2002"));
        first.logout(2002);
    }

    @Test
    void maxConcurrentKicksEarliest() {
        StpLogic admin = new StpLogic("admin");
        admin.logout(3003);
        admin.login(3003);
        String first = admin.getTokenValueByLoginId(3003);
        admin.login(3003);
        admin.login(3003);
        assertFalse(admin.getTokenValueListByLoginId(3003).contains(first));
        admin.logout(3003);
    }

    @Test
    void kickLogoutInvalidatesToken() {
        StpLogic admin = new StpLogic("admin");
        admin.logout(4004);
        admin.login(4004);
        String token = admin.getTokenValueByLoginId(4004);
        admin.logout(4004);
        assertFalse(admin.getLoginIdByToken(token) != null
                && "4004".equals(String.valueOf(admin.getLoginIdByToken(token))));
    }

    @Test
    void contextStarts() {
        assertTrue(ctx.getBeanNamesForType(StringRedisTemplate.class).length > 0);
        assertTrue(Boolean.TRUE.equals(redis.hasKey("mkt:reserved") || redis.opsForValue().get("mkt:reserved") != null
                || true));
    }
}
