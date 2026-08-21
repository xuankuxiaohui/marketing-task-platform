package com.mkt.activity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ActivitySupportDomainTest {

    @Test
    void codesStatusesDatesGrayAndHash() {
        assertThat(ActivityCodes.valid("summer")).isTrue();
        assertThat(ActivityCodes.valid("X")).isFalse();
        assertThat(ActivityStatuses.valid("DRAFT")).isTrue();
        assertThat(ActivityStatuses.deletable("DRAFT")).isTrue();
        assertThat(ActivityStatuses.deletable("PUBLISHED")).isFalse();
        assertThat(ActivityStatuses.publishedFamily("SCHEDULED")).isTrue();
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        assertThat(ActivityDates.today(clock).toString()).isEqualTo("2026-08-20");
        assertThat(ActivityDates.periodKey(clock)).isEqualTo("2026-08-20");
        assertThat(ActivityDates.inWindow(
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-31T00:00:00Z"), clock.instant()))
                .isTrue();
        LocalDateTime utc = ActivityDates.toUtc(clock.instant());
        assertThat(ActivityDates.toInstant(utc)).isEqualTo(clock.instant());
        assertThat(GrayTypes.valid("RATIO")).isTrue();
        assertThat(GrayBuckets.hit("NONE", null, 1L, 2L)).isTrue();
        int bucket = GrayBuckets.of(1L, 2L);
        assertThat(bucket).isBetween(0, 99);
        assertThat(GrayBuckets.hit("RATIO", bucket + 1, 1L, 2L)).isTrue();
        assertThat(GrayBuckets.hit("RATIO", bucket, 1L, 2L)).isFalse();
        assertThat(SubmoduleTypes.valid("TASK")).isTrue();
        assertThat(SubmoduleTypes.valid("AD")).isFalse();
        assertThat(ContentHasher.sha256("x")).hasSize(64);
        assertThat(ContentHasher.sha256(null)).isEqualTo(ContentHasher.sha256(""));
    }
}
