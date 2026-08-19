package com.mkt.spike.springdoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "springdoc.swagger-ui.enabled=false")
class SpringdocUiOffTest {

    @LocalServerPort
    int port;

    @Test
    void swaggerUiDisabled() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        try {
            client.get().uri("/swagger-ui/index.html").retrieve().toBodilessEntity();
            throw new AssertionError("swagger UI should be off");
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            assertTrue(ex.getStatusCode().value() >= 400);
        }
    }
}
