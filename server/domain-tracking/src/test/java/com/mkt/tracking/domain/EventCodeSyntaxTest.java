package com.mkt.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventCodeSyntaxTest {

    @Test
    void acceptsAppendixDShape() {
        assertThat(EventCodeSyntax.valid("page.view")).isTrue();
        assertThat(EventCodeSyntax.valid("page.leave")).isTrue();
        assertThat(EventCodeSyntax.valid("task.card.exposure")).isTrue();
        assertThat(EventCodeSyntax.valid("auth.register.success")).isTrue();
        assertThat(EventCodeSyntax.valid("")).isFalse();
        assertThat(EventCodeSyntax.valid(null)).isFalse();
        assertThat(EventCodeSyntax.valid("Task.Card.Exposure")).isFalse();
    }
}
