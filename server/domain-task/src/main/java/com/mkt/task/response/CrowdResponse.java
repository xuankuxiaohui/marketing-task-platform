package com.mkt.task.response;

import java.time.Instant;

public record CrowdResponse(long id, String code, String name, int itemCount, String status, Instant updatedAt) {}
