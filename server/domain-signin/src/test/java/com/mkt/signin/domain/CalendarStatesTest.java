package com.mkt.signin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarStatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 31);

    @Test
    void fourStatesMatchRecords() {
        List<CalendarStates.SignRecord> records = List.of(
                new CalendarStates.SignRecord(LocalDate.of(2026, 8, 18), SignSources.CHECKIN),
                new CalendarStates.SignRecord(LocalDate.of(2026, 8, 17), SignSources.CATCHUP));
        assertThat(CalendarStates.of(LocalDate.of(2026, 8, 18), TODAY, records, 7, START, END))
                .isEqualTo(CalendarCellState.SIGNED);
        assertThat(CalendarStates.of(LocalDate.of(2026, 8, 17), TODAY, records, 7, START, END))
                .isEqualTo(CalendarCellState.CATCHUP);
        assertThat(CalendarStates.of(LocalDate.of(2026, 8, 19), TODAY, records, 7, START, END))
                .isEqualTo(CalendarCellState.MISSED_CATCHABLE);
        assertThat(CalendarStates.of(TODAY, TODAY, records, 7, START, END))
                .isEqualTo(CalendarCellState.TODAY_AVAILABLE);
        assertThat(CalendarStates.of(LocalDate.of(2026, 8, 10), TODAY, records, 7, START, END))
                .isEqualTo(CalendarCellState.NONE);
        assertThat(CalendarStates.of(LocalDate.of(2026, 8, 21), TODAY, records, 7, START, END))
                .isEqualTo(CalendarCellState.NONE);
    }
}
