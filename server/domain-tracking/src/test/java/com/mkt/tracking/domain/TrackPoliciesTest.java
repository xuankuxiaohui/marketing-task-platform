package com.mkt.tracking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TrackPoliciesTest {

    @Test
    void malformedCodeAndOversizePayloadAreDropped() {
        assertThat(TrackPolicies.decide(
                        "BAD",
                        Map.of(),
                        1024,
                        MetadataStatus.ENABLED,
                        UnregisteredPolicy.ACCEPT,
                        DisabledEventPolicy.DROP_COUNT))
                .isEqualTo(EventFilterDecision.DROP_MALFORMED);
        String big = "x".repeat(2000);
        assertThat(TrackPolicies.decide(
                        "page.view",
                        Map.of("route", big),
                        100,
                        MetadataStatus.ENABLED,
                        UnregisteredPolicy.ACCEPT,
                        DisabledEventPolicy.DROP_COUNT))
                .isEqualTo(EventFilterDecision.DROP_MALFORMED);
    }

    @Test
    void unregisteredFollowsPolicy() {
        assertThat(TrackPolicies.decide(
                        "page.view",
                        Map.of(),
                        1024,
                        MetadataStatus.MISSING,
                        UnregisteredPolicy.ACCEPT,
                        DisabledEventPolicy.DROP_COUNT))
                .isEqualTo(EventFilterDecision.ACCEPT);
        assertThat(TrackPolicies.decide(
                        "page.view",
                        Map.of(),
                        1024,
                        MetadataStatus.MISSING,
                        UnregisteredPolicy.REJECT,
                        DisabledEventPolicy.DROP_COUNT))
                .isEqualTo(EventFilterDecision.DROP_UNREGISTERED);
    }

    @Test
    void disabledFollowsPolicy() {
        assertThat(TrackPolicies.decide(
                        "page.view",
                        Map.of(),
                        1024,
                        MetadataStatus.DISABLED,
                        UnregisteredPolicy.ACCEPT,
                        DisabledEventPolicy.DROP_COUNT))
                .isEqualTo(EventFilterDecision.DROP_DISABLED);
        assertThat(TrackPolicies.decide(
                        "page.view",
                        Map.of(),
                        1024,
                        MetadataStatus.DISABLED,
                        UnregisteredPolicy.ACCEPT,
                        DisabledEventPolicy.KEEP))
                .isEqualTo(EventFilterDecision.ACCEPT);
    }

    @Test
    void enabledIsAccepted() {
        assertThat(TrackPolicies.decide(
                        "task.card.exposure",
                        Map.of("taskId", 1),
                        8192,
                        MetadataStatus.ENABLED,
                        UnregisteredPolicy.REJECT,
                        DisabledEventPolicy.DROP_COUNT))
                .isEqualTo(EventFilterDecision.ACCEPT);
    }
}
