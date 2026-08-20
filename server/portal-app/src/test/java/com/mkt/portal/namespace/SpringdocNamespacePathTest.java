package com.mkt.portal.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

/** RL-08: springdoc JSON/YAML must stay under /api, not the default /v3/api-docs. */
class SpringdocNamespacePathTest {

    @Test
    void apiDocsPathStaysUnderPortalNamespace() {
        assertThat(mainApplicationYml().getProperty("springdoc.api-docs.path")).isEqualTo("/api/v3/api-docs");
    }

    @Test
    void swaggerUiIsDisabled() {
        assertThat(mainApplicationYml().getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    private static Properties mainApplicationYml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(locateMainApplicationYml()));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    private static Path locateMainApplicationYml() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path nested = dir.resolve("portal-app/src/main/resources/application.yml");
            if (Files.isRegularFile(nested)) {
                return nested;
            }
            if ("portal-app".equals(name(dir))) {
                Path local = dir.resolve("src/main/resources/application.yml");
                if (Files.isRegularFile(local)) {
                    return local;
                }
            }
        }
        throw new IllegalStateException("cannot locate portal-app application.yml from " + cwd);
    }

    private static String name(Path dir) {
        Path fileName = dir.getFileName();
        return fileName == null ? "" : fileName.toString();
    }
}
