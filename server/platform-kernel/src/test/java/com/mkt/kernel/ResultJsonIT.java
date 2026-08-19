package com.mkt.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.json.JsonUtil;
import com.mkt.kernel.trace.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@SpringBootTest(classes = KernelTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResultJsonIT {

    @LocalServerPort
    int port;

    @Test
    void successEnvelopeUsesNumericZero() {
        RestClient client = client();
        String body = client.get()
                .uri("/admin/kernel-probe/ok")
                .retrieve()
                .body(String.class);
        JsonNode node = JsonUtil.readTree(body);

        assertThat(node.get("code").isNumber()).isTrue();
        assertThat(node.get("code").intValue()).isZero();
        assertThat(node.get("message").asString()).isEqualTo("ok");
        assertThat(node.get("data").asString()).isEqualTo("pong");
        assertThat(node.get("traceId").asString()).isNotBlank();
        assertThat(body).doesNotContain("\"code\":\"0\"");
    }

    @Test
    void failureEnvelopeUsesStringCode() {
        RestClient client = client();
        var response = client.get()
                .uri("/admin/kernel-probe/biz")
                .retrieve()
                .toEntity(String.class);
        JsonNode node = JsonUtil.readTree(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(node.get("code").isString()).isTrue();
        assertThat(node.get("code").asString()).isEqualTo("common.param-invalid");
        assertThat(node.get("data")).isNull();
        assertThat(node.get("traceId").asString()).isNotBlank();
    }

    @Test
    void serverErrorHidesCauseAndKeepsUnifiedMessage() {
        RestClient client = client();
        var response = client.get()
                .uri("/admin/kernel-probe/boom")
                .retrieve()
                .toEntity(String.class);
        JsonNode node = JsonUtil.readTree(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(node.get("code").asString()).isEqualTo("common.server-error");
        assertThat(node.get("message").asString()).isEqualTo(CommonErrorCodes.SERVER_ERROR.message());
        assertThat(response.getBody()).doesNotContain("jdbc");
    }

    @Test
    void validationFailureIsParamInvalid() {
        RestClient client = client();
        var response = client.get()
                .uri("/admin/kernel-probe/valid?page=0")
                .retrieve()
                .toEntity(String.class);
        JsonNode node = JsonUtil.readTree(response.getBody());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(node.get("code").asString()).isEqualTo("common.param-invalid");
        assertThat(node.get("message").asString()).contains("page");
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
