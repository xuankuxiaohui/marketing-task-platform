package com.mkt.kernel.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.KernelTestApplication;
import com.mkt.kernel.json.JsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@SpringBootTest(classes = KernelTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceIdFilterIT {

    @LocalServerPort
    int port;

    @Test
    void generatedTraceIdIsWrittenToHeaderAndBody() {
        var response = client().get().uri("/admin/kernel-probe/ok").retrieve().toEntity(String.class);
        JsonNode node = JsonUtil.readTree(response.getBody());
        String header = response.getHeaders().getFirst(TraceIds.HEADER);

        assertThat(header).isNotBlank();
        assertThat(header).matches("[a-f0-9]{32}");
        assertThat(node.get("traceId").asString()).isEqualTo(header);
    }

    @Test
    void incomingTraceIdIsReused() {
        var response = client()
                .get()
                .uri("/admin/kernel-probe/ok")
                .header(TraceIds.HEADER, "fixed-trace-id-01")
                .retrieve()
                .toEntity(String.class);
        JsonNode node = JsonUtil.readTree(response.getBody());

        assertThat(response.getHeaders().getFirst(TraceIds.HEADER)).isEqualTo("fixed-trace-id-01");
        assertThat(node.get("traceId").asString()).isEqualTo("fixed-trace-id-01");
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
    }
}
