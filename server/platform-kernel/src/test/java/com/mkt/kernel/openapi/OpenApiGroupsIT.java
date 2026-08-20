package com.mkt.kernel.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.KernelTestApplication;
import com.mkt.kernel.json.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Three-group HTTP export on KernelTestApplication. Must not boot admin-app /
 * portal-app: those classpaths grow AutoConfigurations and would force an exclude list.
 */
@SpringBootTest(classes = KernelTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiGroupsIT {

    @LocalServerPort
    int port;

    @Test
    void threeGroupsExportAndDoNotLeakForeignPaths() {
        RestClient client = RestClient.builder().baseUrl("http://127.0.0.1:" + port).build();
        String admin = client.get().uri("/v3/api-docs/admin").retrieve().body(String.class);
        String portal = client.get().uri("/v3/api-docs/portal").retrieve().body(String.class);
        String internal = client.get().uri("/v3/api-docs/internal").retrieve().body(String.class);

        assertThat(admin).contains("\"openapi\"");
        assertThat(portal).contains("\"openapi\"");
        assertThat(internal).contains("\"openapi\"");

        assertThat(pathKeys(admin))
                .anyMatch(p -> p.startsWith("/admin/"))
                .noneMatch(p -> p.startsWith("/api/") || p.startsWith("/internal/"));
        assertThat(pathKeys(portal))
                .anyMatch(p -> p.startsWith("/api/"))
                .noneMatch(p -> p.startsWith("/admin/") || p.startsWith("/internal/"));
        assertThat(pathKeys(internal))
                .anyMatch(p -> p.startsWith("/internal/"))
                .noneMatch(p -> p.startsWith("/admin/") || p.startsWith("/api/"));
    }

    private static List<String> pathKeys(String body) {
        JsonNode paths = JsonUtil.readTree(body).get("paths");
        assertThat(paths).isNotNull();
        List<String> keys = new ArrayList<>();
        paths.properties().forEach(entry -> keys.add(entry.getKey()));
        return keys;
    }
}
