package com.mkt.kernel.openapi;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Three springdoc groups consumed by task 36. Paths stay in each app's namespace. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(GroupedOpenApi.class)
public class OpenApiGroupsConfiguration {

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("admin").pathsToMatch("/admin/**").build();
    }

    @Bean
    GroupedOpenApi portalApi() {
        return GroupedOpenApi.builder().group("portal").pathsToMatch("/api/**").build();
    }

    @Bean
    GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder().group("internal").pathsToMatch("/internal/**").build();
    }
}
