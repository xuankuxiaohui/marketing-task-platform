package com.mkt.signin.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Maps a UTC+8 day to R36.1 calendar cell state. */
public final class CalendarStates {

    private CalendarStates() {}

    public static CalendarCellState of(
            LocalDate day,
            LocalDate today,
            Collection<SignRecord> records,
            int windowDays,
            LocalDate activityStart,
            LocalDate activityEnd) {
        if (day == null || today == null) {
            return CalendarCellState.NONE;
        }
        SignRecord hit = find(records, day);
        if (hit != null) {
            return SignSources.CATCHUP.equals(hit.source()) ? CalendarCellState.CATCHUP : CalendarCellState.SIGNED;
        }
        if (!inActivity(day, activityStart, activityEnd)) {
            return CalendarCellState.NONE;
        }
        if (day.equals(today)) {
            return CalendarCellState.TODAY_AVAILABLE;
        }
        if (day.isBefore(today) && !day.isBefore(today.minusDays(windowDays))) {
            return CalendarCellState.MISSED_CATCHABLE;
        }
        return CalendarCellState.NONE;
    }

    private static boolean inActivity(LocalDate day, LocalDate start, LocalDate end) {
        if (start != null && day.isBefore(start)) {
            return false;
        }
        return end == null || !day.isAfter(end);
    }

    private static SignRecord find(Collection<SignRecord> records, LocalDate day) {
        if (records == null) {
            return null;
        }
        for (SignRecord record : records) {
            if (day.equals(record.signDate())) {
                return record;
            }
        }
        return null;
    }

    public static Set<LocalDate> datesOf(Collection<SignRecord> records) {
        Set<LocalDate> dates = new HashSet<>();
        if (records == null) {
            return dates;
        }
        for (SignRecord record : records) {
            dates.add(record.signDate());
        }
        return dates;
    }

    public record SignRecord(LocalDate signDate, String source) {}
}
