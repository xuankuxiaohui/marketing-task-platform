package com.mkt.identity.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.command.AdminLoginCommand;
import com.mkt.kernel.json.JsonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;

class AuditSummariesTest {

    @Test
    void masksPasswordTokenSecretAndPhoneThenTruncates() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "alice");
        body.put("password", "Abcdef12!x");
        body.put("token", "admin:raw-secret-token");
        body.put("secret", "hmac-secret");
        body.put("phone", "13800138000");
        String summary = AuditSummaries.ofValue(body);
        JsonNode node = JsonUtil.readTree(summary);
        assertThat(node.get("username").asString()).isEqualTo("alice");
        assertThat(node.get("password").asString()).doesNotContain("Abcdef12");
        assertThat(node.get("token").asString()).doesNotContain("raw-secret-token");
        assertThat(node.get("secret").asString()).doesNotContain("hmac-secret");
        assertThat(node.get("phone").asString()).doesNotContain("13800138000");
        assertThat(node.get("phone").asString()).contains("*");
    }

    @Test
    void loginArgsDropServletAndMaskPassword() {
        String summary = AuditSummaries.ofArgs(new Object[] {
            new AdminLoginCommand("alice", "Abcdef12!x", "cid", "code", null), new MockHttpServletRequest()
        });
        assertThat(summary).contains("alice");
        assertThat(summary).doesNotContain("Abcdef12!x");
        assertThat(summary).doesNotContain("MockHttpServletRequest");
    }

    @Test
    void truncateAppendsMarkerAt2000() {
        String longValue = "x".repeat(2100);
        String truncated = AuditSummaries.truncate(longValue, 2000);
        assertThat(truncated).hasSize(2000);
        assertThat(truncated).endsWith("...(truncated)");
        assertThat(AuditSummaries.cut("abcdefghij", 4)).isEqualTo("abcd");
    }
}
