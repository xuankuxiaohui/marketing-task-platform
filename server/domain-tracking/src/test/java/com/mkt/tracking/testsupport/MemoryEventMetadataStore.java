package com.mkt.tracking.testsupport;

import com.mkt.tracking.application.EventMetadataStore;
import com.mkt.tracking.domain.MetadataStatus;
import java.util.HashMap;
import java.util.Map;

public final class MemoryEventMetadataStore implements EventMetadataStore {

    private final Map<String, MetadataStatus> statuses = new HashMap<>();

    public MemoryEventMetadataStore put(String code, MetadataStatus status) {
        statuses.put(code, status);
        return this;
    }

    @Override
    public MetadataStatus statusOf(String eventCode) {
        return statuses.getOrDefault(eventCode, MetadataStatus.MISSING);
    }
}
