package com.mkt.kernel.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

class JsonUtilTest {

    @Test
    void roundTripAndIsoInstant() {
        Sample sample = new Sample("n", Instant.parse("2026-08-16T04:00:00Z"));
        String json = JsonUtil.toJson(sample);

        assertThat(json).contains("2026-08-16T04:00:00Z");
        assertThat(JsonUtil.fromJson(json, Sample.class)).isEqualTo(sample);
        assertThat(JsonUtil.fromJson(json, new TypeReference<Sample>() {})).isEqualTo(sample);
        assertThat(JsonUtil.readTree(json).get("name").asString()).isEqualTo("n");
        assertThat(JsonUtil.mapper()).isSameAs(JsonUtil.mapper());
    }

    @Test
    void invalidJsonFailsFast() {
        assertThatThrownBy(() -> JsonUtil.fromJson("{", Sample.class))
                .isInstanceOf(IllegalStateException.class);
    }

    record Sample(String name, Instant at) {
    }
}
