package com.mkt.identity.response;

import java.util.List;

/** Cached enabled dict entries for a type code. */
public record DictEntriesCache(List<DictEntryOption> items) {

    public DictEntriesCache {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
