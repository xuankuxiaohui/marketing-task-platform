package com.mkt.kernel;

import java.util.List;

/** Appendix C page payload: {@code {total, records}}. */
public record PageData<T>(long total, List<T> records) {

    public PageData {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
