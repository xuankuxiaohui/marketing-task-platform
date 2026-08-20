package com.mkt.signin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class CalendarStatesPropertyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 31);

    @Property(tries = 80)
    void cellStateMatchesRecordSet(
            @ForAll @IntRange(min = 1, max = 31) int day,
            @ForAll @IntRange(min = 0, max = 1) int catchupFlag,
            @ForAll @IntRange(min = 1, max = 14) int window) {
        LocalDate signed = LocalDate.of(2026, 8, Math.min(day, 20));
        String source = catchupFlag == 1 ? SignSources.CATCHUP : SignSources.CHECKIN;
        List<CalendarStates.SignRecord> records = List.of(new CalendarStates.SignRecord(signed, source));
        for (int d = 1; d <= 31; d++) {
            LocalDate cursor = LocalDate.of(2026, 8, d);
            CalendarCellState state = CalendarStates.of(cursor, TODAY, records, window, START, END);
            if (cursor.equals(signed)) {
                assertThat(state)
                        .isEqualTo(catchupFlag == 1 ? CalendarCellState.CATCHUP : CalendarCellState.SIGNED);
            } else if (cursor.equals(TODAY)) {
                assertThat(state).isEqualTo(CalendarCellState.TODAY_AVAILABLE);
            } else if (cursor.isBefore(TODAY) && !cursor.isBefore(TODAY.minusDays(window))) {
                assertThat(state).isEqualTo(CalendarCellState.MISSED_CATCHABLE);
            } else {
                assertThat(state).isEqualTo(CalendarCellState.NONE);
            }
        }
    }

    @Property(tries = 40)
    void consecutiveDaysWalksBackFromLiveEnd(@ForAll @IntRange(min = 1, max = 10) int length) {
        Set<LocalDate> dates = new HashSet<>();
        for (int i = 0; i < length; i++) {
            dates.add(TODAY.minusDays(i));
        }
        assertThat(ConsecutiveDays.compute(dates, TODAY)).isEqualTo(length);
        dates.remove(TODAY.minusDays(length == 1 ? 0 : length / 2));
        if (length == 1) {
            assertThat(ConsecutiveDays.compute(dates, TODAY)).isZero();
        }
    }
}
