package com.mkt.activity.response;

import java.time.Instant;

public record ParticipationView(
        long id, long activityId, long userId, String periodKey, String result, String hitRule, Instant createdAt) {}
