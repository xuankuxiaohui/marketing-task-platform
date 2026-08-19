package com.mkt.tracking.domain;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** {@code evt_event_log} monthly RANGE partition names (design §3.7 / §6.7-8). */
public final class PartitionNames {

    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter BOUND = DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00");

    private PartitionNames() {
    }

    public static String name(YearMonth month) {
        return "p" + month.format(NAME);
    }

    public static String lessThanBound(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toLocalDate().format(BOUND);
    }

    public static YearMonth parseName(String partitionName) {
        if (partitionName == null || !partitionName.startsWith("p") || partitionName.length() != 7) {
            return null;
        }
        try {
            return YearMonth.parse(partitionName.substring(1), NAME);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
