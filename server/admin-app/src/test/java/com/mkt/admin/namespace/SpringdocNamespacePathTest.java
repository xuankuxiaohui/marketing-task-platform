package com.mkt.admin.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

/** RL-08 / P1: springdoc under /admin; Swagger UI off; prod closes JSON export. */
class SpringdocNamespacePathTest {

    @Test
    void apiDocsPathStaysUnderAdminNamespace() {
        assertThat(mainApplicationYml().getProperty("springdoc.api-docs.path")).isEqualTo("/admin/v3/api-docs");
    }

    @Test
    void swaggerUiIsDisabled() {
        assertThat(mainApplicationYml().getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void prodProfileDisablesApiDocsJson() {
        assertThat(prodApplicationYml().getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
    }

    private static Properties mainApplicationYml() {
        return loadYaml(locateResource("application.yml"));
    }

    private static Properties prodApplicationYml() {
        return loadYaml(locateResource("application-prod.yml"));
    }

    private static Properties loadYaml(Path path) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(path));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    private static Path locateResource(String fileName) {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path nested = dir.resolve("admin-app/src/main/resources/" + fileName);
            if (Files.isRegularFile(nested)) {
                return nested;
            }
            if ("admin-app".equals(name(dir))) {
                Path local = dir.resolve("src/main/resources/" + fileName);
                if (Files.isRegularFile(local)) {
                    return local;
                }
            }
        }
        throw new IllegalStateException("cannot locate admin-app " + fileName + " from " + cwd);
    }

    private static String name(Path dir) {
        Path fileName = dir.getFileName();
        return fileName == null ? "" : fileName.toString();
    }
}
