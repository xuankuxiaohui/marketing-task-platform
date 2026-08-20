package com.mkt.signin.response;

import java.time.Instant;

public record PortalActivityView(long activityId, String code, String name, Instant startTime, Instant endTime) {}
