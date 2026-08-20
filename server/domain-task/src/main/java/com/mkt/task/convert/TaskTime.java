package com.mkt.task.convert;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class TaskTime {

    private TaskTime() {}

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime toUtc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
