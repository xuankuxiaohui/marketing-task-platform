package com.mkt.admin.metrics;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** Period keys for daily rows rolled to week / month (UTC+8 calendar). */
public final class MetricsPeriods {

    private static final WeekFields ISO = WeekFields.ISO;

    private MetricsPeriods() {}

    public static String key(LocalDate day, MetricsGrain grain) {
        return switch (grain) {
            case DAY -> day.toString();
            case WEEK -> {
                int week = day.get(ISO.weekOfWeekBasedYear());
                int year = day.get(ISO.weekBasedYear());
                yield year + "-W" + pad2(week);
            }
            case MONTH -> day.getYear() + "-" + pad2(day.getMonthValue());
        };
    }

    private static String pad2(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }
}
