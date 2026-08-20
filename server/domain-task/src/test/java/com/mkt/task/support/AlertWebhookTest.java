package com.mkt.task.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertWebhookTest {

    @Test
    void onlyHttpsPublicHostsAreAccepted() {
        assertThat(AlertWebhook.httpsPublicUri("")).isNull();
        assertThat(AlertWebhook.httpsPublicUri("http://example.com/hook")).isNull();
        assertThat(AlertWebhook.httpsPublicUri("https://127.0.0.1/hook")).isNull();
        assertThat(AlertWebhook.httpsPublicUri("https://localhost/hook")).isNull();
        assertThat(AlertWebhook.httpsPublicUri("https://10.0.0.1/hook")).isNull();
        assertThat(AlertWebhook.httpsPublicUri("https://example.com/hook")).isNotNull();
    }
}
