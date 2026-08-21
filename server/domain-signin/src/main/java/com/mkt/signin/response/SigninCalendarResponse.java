package com.mkt.signin.response;

import java.util.List;

public record SigninCalendarResponse(
        long activityId,
        String activityCode,
        String activityName,
        String yearMonth,
        int consecutiveDays,
        int catchupWindowDays,
        int catchupDailyLimit,
        int catchupCostPoints,
        long pointsBalance,
        Integer nextRewardDay,
        Long nextRewardPrizeId,
        String nextRewardHint,
        List<CalendarDayView> days,
        List<SigninTierView> tiers) {}
