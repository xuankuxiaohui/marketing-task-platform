package com.mkt.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventBatchCodesTest {

    @Test
    void expandsJsonArrayBeyondRowEventCode() {
        String events = "[{\"code\":\"page.view\",\"props\":{}},{\"code\":\"page.leave\",\"props\":{}}]";
        assertThat(EventBatchCodes.matches("page.view", events, "page.leave")).isTrue();
        assertThat(EventBatchCodes.matches("page.view", events, "page.view")).isTrue();
        assertThat(EventBatchCodes.matches("page.view", events, "task.start.click")).isFalse();
    }

    @Test
    void blankFilterMatchesAll() {
        assertThat(EventBatchCodes.matches("page.view", "[]", null)).isTrue();
        assertThat(EventBatchCodes.matches("page.view", "[]", "")).isTrue();
    }
}
