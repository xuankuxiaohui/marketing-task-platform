package com.mkt.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.json.JsonUtil;
import com.mkt.kernel.trace.TraceIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ResultTest {

    @AfterEach
    void clearMdc() {
        TraceIds.clear();
    }

    @Test
    void successCodeIsNumberZero() {
        TraceIds.put("trace-ok");
        String json = JsonUtil.toJson(Result.ok("pong"));
        JsonNode node = JsonUtil.readTree(json);

        assertThat(node.get("code").isNumber()).isTrue();
        assertThat(node.get("code").intValue()).isZero();
        assertThat(node.get("message").asString()).isEqualTo("ok");
        assertThat(node.get("data").asString()).isEqualTo("pong");
        assertThat(node.get("traceId").asString()).isEqualTo("trace-ok");
        assertThat(json).doesNotContain("\"code\":\"0\"");
    }

    @Test
    void failureCodeIsStringAndOmitsData() {
        TraceIds.put("trace-fail");
        String json = JsonUtil.toJson(Result.fail(CommonErrorCodes.PARAM_INVALID));
        JsonNode node = JsonUtil.readTree(json);

        assertThat(node.get("code").isString()).isTrue();
        assertThat(node.get("code").asString()).isEqualTo("common.param-invalid");
        assertThat(node.get("message").asString()).isEqualTo(CommonErrorCodes.PARAM_INVALID.message());
        assertThat(node.get("data")).isNull();
        assertThat(node.get("traceId").asString()).isEqualTo("trace-fail");
    }
}
