package com.mkt.kernel.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

class OpenApiGroupsConfigurationTest {

    @Test
    void threeGroupsMatchNamespacePaths() {
        OpenApiGroupsConfiguration config = new OpenApiGroupsConfiguration();
        GroupedOpenApi admin = config.adminApi();
        GroupedOpenApi portal = config.portalApi();
        GroupedOpenApi internal = config.internalApi();

        assertThat(admin.getGroup()).isEqualTo("admin");
        assertThat(portal.getGroup()).isEqualTo("portal");
        assertThat(internal.getGroup()).isEqualTo("internal");
        assertThat(admin.getPathsToMatch()).containsExactly("/admin/**");
        assertThat(portal.getPathsToMatch()).containsExactly("/api/**");
        assertThat(internal.getPathsToMatch()).containsExactly("/internal/**");
    }
}
