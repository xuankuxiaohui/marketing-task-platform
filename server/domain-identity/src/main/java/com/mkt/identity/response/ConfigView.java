package com.mkt.identity.response;

import java.time.Instant;

public record ConfigView(
        long id,
        String configKey,
        String configGroup,
        String configValue,
        String valueType,
        boolean masked,
        String status,
        String remark,
        Instant updatedAt) {}
