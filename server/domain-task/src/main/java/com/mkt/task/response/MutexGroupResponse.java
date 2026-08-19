package com.mkt.task.response;

import java.time.Instant;

public record MutexGroupResponse(long id, String code, String name, boolean crossCycle, Instant createdAt) {}
