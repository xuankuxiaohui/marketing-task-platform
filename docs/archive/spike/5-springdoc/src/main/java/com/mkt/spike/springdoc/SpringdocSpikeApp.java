package com.mkt.spike.springdoc;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class SpringdocSpikeApp {

    public static void main(String[] args) {
        SpringApplication.run(SpringdocSpikeApp.class, args);
    }

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

    @RestController
    static class AdminController {
        @GetMapping("/admin/ping")
        String ping() {
            return "admin";
        }
    }

    @RestController
    static class PortalController {
        @GetMapping("/api/ping")
        String ping() {
            return "portal";
        }
    }

    @RestController
    static class InternalController {
        @GetMapping("/internal/ping")
        String ping() {
            return "internal";
        }
    }
}
