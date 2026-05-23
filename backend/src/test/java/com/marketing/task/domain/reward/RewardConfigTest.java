package com.marketing.task.domain.reward;

import com.marketing.task.service.reward.RewardConfigParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardConfigTest {

    private RewardConfigParser parser;

    @BeforeEach
    void setUp() {
        parser = new RewardConfigParser();
    }

    @Test
    void shouldParsePointConfig() {
        RewardConfig config = parser.parse("{\"type\":\"point\",\"amount\":10}");
        assertEquals("point", config.getType());
        assertEquals(10, config.getAmount());
    }

    @Test
    void shouldParseCouponConfig() {
        RewardConfig config = parser.parse("{\"type\":\"coupon\",\"amount\":1}");
        assertEquals("coupon", config.getType());
        assertEquals(1, config.getAmount());
    }

    @Test
    void shouldParseBadgeConfig() {
        RewardConfig config = parser.parse("{\"type\":\"badge\",\"name\":\"reader\"}");
        assertEquals("badge", config.getType());
        assertEquals("reader", config.getName());
    }

    @Test
    void shouldHandleNullJson() {
        RewardConfig config = parser.parse(null);
        assertNull(config.getType());
    }

    @Test
    void shouldHandleBlankJson() {
        RewardConfig config = parser.parse("   ");
        assertNull(config.getType());
    }

    @Test
    void shouldHandleInvalidJson() {
        RewardConfig config = parser.parse("{invalid}");
        assertEquals("unknown", config.getType());
    }

    @Test
    void shouldHandleEmptyJson() {
        RewardConfig config = parser.parse("{}");
        assertNull(config.getType());
    }

    @Test
    void shouldParseExtraFields() {
        RewardConfig config = parser.parse("{\"type\":\"point\",\"amount\":50,\"reason\":\"bonus\"}");
        assertEquals("point", config.getType());
        assertEquals(50, config.getAmount());
        assertNotNull(config.getExtra());
        assertEquals("bonus", config.getExtra().get("reason"));
    }
}
