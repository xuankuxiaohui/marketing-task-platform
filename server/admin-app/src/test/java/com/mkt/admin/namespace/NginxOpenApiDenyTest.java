package com.mkt.admin.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** P1 / R31: public gateway must deny OpenAPI JSON and Swagger UI paths. */
class NginxOpenApiDenyTest {

    @Test
    void publicNginxDeniesApiDocsAndSwaggerUi() throws IOException {
        String conf = Files.readString(locateNginxConf());
        assertThat(conf).contains("location ~* /v3/api-docs");
        assertThat(conf).contains("location ~* /swagger-ui");
        assertThat(conf).containsPattern("(?s)location ~\\* /v3/api-docs\\s*\\{\\s*return 404;");
        assertThat(conf).containsPattern("(?s)location ~\\* /swagger-ui\\s*\\{\\s*return 404;");
    }

    private static Path locateNginxConf() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("deploy/nginx/nginx.conf");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("cannot locate deploy/nginx/nginx.conf from " + cwd);
    }
}
