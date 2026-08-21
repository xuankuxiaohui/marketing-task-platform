package com.mkt.activity.query;

import com.mkt.kernel.PageQuery;

public record ParticipationQuery(Long activityId, Long userId, String result, String periodKey, PageQuery page) {}
