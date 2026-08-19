package com.mkt.portal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

// OpenAPI JSON only. @SpringBootTest properties replace yml exclude, so
// restated in full. springdoc path must be explicit so yaml maps under /api.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.autoconfigure.exclude="
                    + "com.mkt.risk.RiskAutoConfiguration,"
                    + "com.mkt.risk.RiskAdminAutoConfiguration,"
                    + "com.mkt.tracking.TrackingAutoConfiguration,"
                    + "com.mkt.tracking.TrackingAdminAutoConfiguration,"
                    + "com.mkt.tracking.TrackingPortalAutoConfiguration,"
                    + "com.mkt.task.TaskAutoConfiguration,"
                    + "com.mkt.task.TaskAdminAutoConfiguration,"
                    + "com.mkt.identity.IdentityAutoConfiguration,"
                    + "com.mkt.identity.IdentityAdminAutoConfiguration,"
                    + "com.mkt.identity.IdentityPortalAutoConfiguration,"
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "com.mkt.infra.InfraAutoConfiguration",
            "springdoc.api-docs.path=/api/v3/api-docs",
            "springdoc.swagger-ui.enabled=false"
        })
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
