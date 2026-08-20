package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class CycleKeyResolverTest {

    @Test
    void dailyAndMonthlyUseShanghaiCalendar() {
        Instant now = Instant.parse("2026-08-19T16:30:00Z");
        assertThat(CycleKeyResolver.resolve(CycleTypes.NONE, null, null, null, now)).isEqualTo("NONE");
        assertThat(CycleKeyResolver.resolve(CycleTypes.DAILY, null, null, null, now)).isEqualTo("20260820");
        assertThat(CycleKeyResolver.resolve(CycleTypes.MONTHLY, null, null, null, now)).isEqualTo("202608");
    }

    @Test
    void specialFormatsStartAndEnd() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-10T00:00:00Z");
        assertThat(CycleKeyResolver.resolve(CycleTypes.SPECIAL, null, start, end, Instant.parse("2026-08-05T00:00:00Z")))
                .isEqualTo("20260801080000-20260810080000");
    }

    @Test
    void cronKeyIsLastTrigger() {
        Instant now = Instant.parse("2026-08-19T04:10:00Z");
        String key = CycleKeyResolver.resolve(CycleTypes.CRON, "0 * * * *", null, null, now);
        ZonedDateTime last = CronExprs.lastAtOrBefore("0 * * * *", now.atZone(CycleKeyResolver.ZONE));
        assertThat(key).isEqualTo(last.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
    }

    @Test
    void cycleEndIsNextStartMinusOneMs() {
        Instant now = Instant.parse("2026-08-19T04:00:00Z");
        Instant end = CycleKeyResolver.cycleEnd(CycleTypes.DAILY, null, null, null, now);
        assertThat(end).isEqualTo(Instant.parse("2026-08-19T15:59:59.999Z"));
    }
}
