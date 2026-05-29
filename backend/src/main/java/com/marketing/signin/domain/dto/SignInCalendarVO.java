package com.marketing.signin.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignInCalendarVO {
    private String periodKey;
    private Integer currentStreak;
    private Integer totalSignedDays;
    private List<DayEntry> days;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayEntry {
        private LocalDate date;
        private boolean signed;
        private Integer streakDay;
        private Integer points;
        private boolean catchUp;
    }
}
