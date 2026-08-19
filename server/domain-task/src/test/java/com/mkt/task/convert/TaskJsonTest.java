package com.mkt.task.convert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskJsonTest {

    @Test
    void roundTripLongsAndMaps() {
        assertThat(TaskJson.longs((List<Long>) null)).isNull();
        assertThat(TaskJson.longs((String) null)).isEmpty();
        String json = TaskJson.longs(List.of(1L, 2L));
        assertThat(TaskJson.longs(json)).containsExactly(1L, 2L);
        assertThat(TaskJson.map((Map<String, Object>) null)).isNull();
        assertThat(TaskJson.map((String) null)).isEmpty();
        String obj = TaskJson.map(Map.of("route", "home"));
        assertThat(TaskJson.map(obj)).containsEntry("route", "home");
    }

    @Test
    void timeRoundTrip() {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        LocalDateTime utc = TaskTime.toUtc(now);
        assertThat(TaskTime.toInstant(utc)).isEqualTo(now);
        assertThat(TaskTime.toUtc(null)).isNull();
        assertThat(TaskTime.toInstant(null)).isNull();
        assertThat(utc.toInstant(ZoneOffset.UTC)).isEqualTo(now);
    }
}
