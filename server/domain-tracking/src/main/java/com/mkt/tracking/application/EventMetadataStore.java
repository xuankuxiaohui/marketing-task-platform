package com.mkt.tracking.application;

import com.mkt.tracking.domain.MetadataStatus;

public interface EventMetadataStore {

    MetadataStatus statusOf(String eventCode);
}
