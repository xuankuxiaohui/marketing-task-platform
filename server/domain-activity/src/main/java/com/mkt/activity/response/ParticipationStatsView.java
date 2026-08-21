package com.mkt.activity.response;

import java.util.List;

public record ParticipationStatsView(
        long total, long passCount, long rejectCount, double passRate, List<HitRuleCountView> rejectReasons) {}
