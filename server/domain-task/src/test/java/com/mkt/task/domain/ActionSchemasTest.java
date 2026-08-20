package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionSchemasTest {

    @Test
    void acceptsClosedSchemas() {
        assertThat(ActionSchemas.validPlatform("WEB")).isTrue();
        assertThat(ActionSchemas.validPlatform("PC")).isFalse();
        assertThat(ActionSchemas.validType("ROUTE")).isTrue();
        assertThat(ActionSchemas.validParams("NONE", Map.of(), null)).isTrue();
        assertThat(ActionSchemas.validParams("ROUTE", Map.of("route", "home"), "去")).isTrue();
        assertThat(ActionSchemas.validParams("LINK", Map.of("url", "https://a.example/x"), null)).isTrue();
        assertThat(ActionSchemas.validParams("LINK", Map.of("url", "http://insecure"), null)).isFalse();
        assertThat(ActionSchemas.validParams("SCHEME", Map.of("scheme", "app://x"), "too-long-button-text")).isFalse();
        assertThat(ActionSchemas.validParams("SCHEME", Map.of("scheme", "app://x"), "打开")).isTrue();
        assertThat(ActionSchemas.validParams("ROUTE", Map.of(), null)).isFalse();
        assertThat(DefinitionStatuses.valid("DRAFT")).isTrue();
        assertThat(DefinitionStatuses.deletable("DRAFT")).isTrue();
        assertThat(DefinitionStatuses.publishedFamily("PUBLISHED")).isTrue();
        assertThat(GrayTypes.valid("RATIO")).isTrue();
        assertThat(TaskCodes.valid("daily_check")).isTrue();
        assertThat(TaskCodes.valid("X")).isFalse();
        assertThat(CrowdStatuses.valid("ENABLED")).isTrue();
        assertThat(StepTypes.valid("REWARD")).isTrue();
        assertThat(CycleTypes.valid("CRON")).isTrue();
    }
}
