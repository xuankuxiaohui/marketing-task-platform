package com.mkt.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstanceStatusesTest {

    @Test
    void mineFilterMapsClosedEnumAliasesAndTabIndexToCompleted() {
        assertThat(InstanceStatuses.mineFilter("COMPLETED")).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(InstanceStatuses.mineFilter("completed")).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(InstanceStatuses.mineFilter("SUCCESS")).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(InstanceStatuses.mineFilter("DONE")).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(InstanceStatuses.mineFilter("1")).isEqualTo(InstanceStatuses.COMPLETED);
        assertThat(InstanceStatuses.mineFilter("IN_PROGRESS")).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(InstanceStatuses.mineFilter("0")).isEqualTo(InstanceStatuses.IN_PROGRESS);
        assertThat(InstanceStatuses.mineFilter("ABANDONED")).isEqualTo(InstanceStatuses.ABANDONED);
        assertThat(InstanceStatuses.mineFilter("EXPIRED")).isEqualTo(InstanceStatuses.EXPIRED);
        assertThat(InstanceStatuses.mineFilter("  ")).isNull();
        assertThat(InstanceStatuses.mineFilter(null)).isNull();
    }
}
