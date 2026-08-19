package com.mkt.task.response;

import java.time.Instant;

public record TaskDefinitionView(
        long id,
        String code,
        String name,
        String category,
        String status,
        int version,
        int sortWeight,
        Instant updatedAt) {}
