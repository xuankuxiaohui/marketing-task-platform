package com.mkt.portal;

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
    void portalAppExportsThreeGroups() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        String admin = client.get().uri("/api/v3/api-docs/admin").retrieve().body(String.class);
        String portal = client.get().uri("/api/v3/api-docs/portal").retrieve().body(String.class);
        String internal = client.get().uri("/api/v3/api-docs/internal").retrieve().body(String.class);

        assertThat(admin).contains("\"openapi\"");
        assertThat(portal).contains("\"openapi\"");
        assertThat(internal).contains("\"openapi\"");
        assertThat(portal).doesNotContain("/admin/");
        assertThat(internal).doesNotContain("/admin/");
    }
}
