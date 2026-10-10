package com.mkt.tracking.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.tracking.domain.MetadataStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryEventMetadataStoreTest {

    @Test
    void statusesOfEmptyOrNullReturnsEmptyMap() {
        MemoryEventMetadataStore store = new MemoryEventMetadataStore().put("page.view", MetadataStatus.ENABLED);
        assertThat(store.statusesOf(null)).isEmpty();
        assertThat(store.statusesOf(List.of())).isEmpty();
    }

    @Test
    void statusesOfMapsMissingAndKnownCodes() {
        MemoryEventMetadataStore store = new MemoryEventMetadataStore()
                .put("page.view", MetadataStatus.ENABLED)
                .put("old.event.code", MetadataStatus.DISABLED);

        Map<String, MetadataStatus> statuses =
                store.statusesOf(List.of("page.view", "unknown.event", "old.event.code", "page.view"));

        assertThat(statuses)
                .containsEntry("page.view", MetadataStatus.ENABLED)
                .containsEntry("unknown.event", MetadataStatus.MISSING)
                .containsEntry("old.event.code", MetadataStatus.DISABLED);
        assertThat(statuses).hasSize(3);
    }

    @Test
    void statusesOfSkipsNullCodes() {
        MemoryEventMetadataStore store = new MemoryEventMetadataStore().put("page.view", MetadataStatus.ENABLED);
        Map<String, MetadataStatus> statuses = store.statusesOf(Arrays.asList(null, "page.view", null));
        assertThat(statuses).containsOnlyKeys("page.view");
        assertThat(statuses.get("page.view")).isEqualTo(MetadataStatus.ENABLED);
    }
}
