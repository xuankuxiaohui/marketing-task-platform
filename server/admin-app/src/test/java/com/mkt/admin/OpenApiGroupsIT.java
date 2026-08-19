package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiGroupsIT {

    @LocalServerPort
    int port;

    @Test
    void adminAppExportsThreeGroups() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        String admin = client.get().uri("/admin/v3/api-docs/admin").retrieve().body(String.class);
        String portal = client.get().uri("/admin/v3/api-docs/portal").retrieve().body(String.class);
        String internal = client.get().uri("/admin/v3/api-docs/internal").retrieve().body(String.class);

        assertThat(admin).contains("\"openapi\"");
        assertThat(portal).contains("\"openapi\"");
        assertThat(internal).contains("\"openapi\"");
        assertThat(admin).doesNotContain("/api/");
        assertThat(admin).doesNotContain("/internal/");
    }
}
