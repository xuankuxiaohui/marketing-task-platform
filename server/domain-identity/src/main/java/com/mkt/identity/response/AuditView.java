package com.mkt.identity.response;

import java.time.Instant;

public record AuditView(
        long id,
        String module,
        String action,
        Long operatorId,
        String operatorName,
        String ip,
        String userAgent,
        String requestSummary,
        String result,
        String errorMessage,
        Integer costMs,
        String traceId,
        Instant createdAt) {}
