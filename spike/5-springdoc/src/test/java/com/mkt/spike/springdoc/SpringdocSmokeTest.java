package com.mkt.spike.springdoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringdocSmokeTest {

    @LocalServerPort
    int port;

    @Test
    void threeGroupsExportWithoutPathLeak() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        String admin = client.get().uri("/v3/api-docs/admin").retrieve().body(String.class);
        String portal = client.get().uri("/v3/api-docs/portal").retrieve().body(String.class);
        String internal = client.get().uri("/v3/api-docs/internal").retrieve().body(String.class);
        assertTrue(admin.contains("/admin/ping"));
        assertFalse(admin.contains("/api/ping"));
        assertFalse(admin.contains("/internal/ping"));
        assertTrue(portal.contains("/api/ping"));
        assertFalse(portal.contains("/admin/ping"));
        assertTrue(internal.contains("/internal/ping"));
        assertFalse(internal.contains("/admin/ping"));
    }
}
