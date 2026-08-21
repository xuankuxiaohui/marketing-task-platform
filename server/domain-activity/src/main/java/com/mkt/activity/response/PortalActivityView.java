package com.mkt.activity.response;

import java.time.Instant;

public record PortalActivityView(long id, String code, String name, Instant startTime, Instant endTime) {}
