package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FlywayAdminOnlyTest {

    @Test
    void adminEnablesFlywayPortalDisables() throws IOException {
        String admin = Files.readString(appYml("admin-app"));
        String portal = Files.readString(appYml("portal-app"));
        assertThat(admin).contains("flyway:").contains("enabled: true");
        assertThat(portal).contains("flyway:").contains("enabled: false");
    }

    private static Path appYml(String module) {
        Path cwd = Path.of("").toAbsolutePath();
        Path server = cwd.getFileName().toString().equals("admin-app") ? cwd.getParent() : cwd.resolve("server");
        return server.resolve(module).resolve("src/main/resources/application.yml");
    }
}
