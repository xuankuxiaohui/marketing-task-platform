package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CronExprsTest {

    @Test
    void hourlyOkHalfHourRejected() {
        assertThat(CronExprs.valid("0 * * * *")).isTrue();
        assertThat(CronExprs.valid("0 */2 * * *")).isTrue();
        assertThat(CronExprs.valid("*/30 * * * *")).isFalse();
        assertThat(CronExprs.valid("0,30 * * * *")).isFalse();
        assertThat(CronExprs.valid("not-a-cron")).isFalse();
    }
}
