package com.mkt.tracking.query;

import com.mkt.kernel.PageQuery;

public record TrackMetadataQuery(String eventCode, String status, PageQuery page) {

    public TrackMetadataQuery {
        page = page == null ? PageQuery.of(null, null) : page;
    }
}
