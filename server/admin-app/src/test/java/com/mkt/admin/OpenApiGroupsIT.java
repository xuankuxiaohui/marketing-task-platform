package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

// OpenAPI JSON only; no DataSource / Redis. @SpringBootTest exclude
// replaces the application.yml list, so DataSource and Infra must be restated.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties =
                "spring.autoconfigure.exclude="
                        + "com.mkt.risk.RiskAutoConfiguration,"
                        + "com.mkt.risk.RiskAdminAutoConfiguration,"
                        + "com.mkt.tracking.TrackingAutoConfiguration,"
                        + "com.mkt.tracking.TrackingAdminAutoConfiguration,"
                        + "com.mkt.tracking.TrackingPortalAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "com.mkt.infra.InfraAutoConfiguration")
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
