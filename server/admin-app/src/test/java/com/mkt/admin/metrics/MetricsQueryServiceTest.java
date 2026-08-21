package com.mkt.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MetricsQueryServiceTest {

    @Test
    void defaultRangeIsLastSevenShanghaiDays() {
        Instant noonUtc = Instant.parse("2026-08-20T04:00:00Z");
        MetricsQueryService queries = new MetricsQueryService(new JdbcTemplate(), Clock.fixed(noonUtc, ZoneOffset.UTC));
        MetricsQueryService.DateRange range = queries.range(new MetricsQuery(null, null, MetricsGrain.DAY, null));
        assertThat(range.from().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(range.toExclusive().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void inclusiveInstantEndExtendsExclusiveDay() {
        Instant from = OffsetDateTime.parse("2026-08-01T00:00:00+08:00").toInstant();
        Instant to = OffsetDateTime.parse("2026-08-20T12:00:00+08:00").toInstant();
        MetricsQueryService queries =
                new MetricsQueryService(new JdbcTemplate(), Clock.systemUTC());
        MetricsQueryService.DateRange range =
                queries.range(new MetricsQuery(from, to, MetricsGrain.DAY, null));
        assertThat(range.from().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(range.toExclusive().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void emptyJdbcYieldsEmptyRecords() {
        MetricsAggregateServiceTest.RecordingJdbc jdbc = new MetricsAggregateServiceTest.RecordingJdbc();
        Instant noonUtc = Instant.parse("2026-08-20T04:00:00Z");
        MetricsQueryService queries = new MetricsQueryService(jdbc, Clock.fixed(noonUtc, ZoneOffset.UTC));
        MetricsQuery q = new MetricsQuery(null, null, MetricsGrain.DAY, "1");
        assertThat(queries.funnel(q).records()).isEmpty();
        assertThat(queries.spend(q).records()).isEmpty();
        assertThat(queries.risk(q).records()).isEmpty();
        assertThat(queries.ad(new MetricsQuery(null, null, MetricsGrain.DAY, "home")).records()).isEmpty();
        Instant start = OffsetDateTime.parse("2026-08-20T00:00:00+08:00").toInstant();
        queries.range(new MetricsQuery(start, start, MetricsGrain.DAY, null));
        queries.range(new MetricsQuery(start, null, MetricsGrain.DAY, " "));
    }
}
