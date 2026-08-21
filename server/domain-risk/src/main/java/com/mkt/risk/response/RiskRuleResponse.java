package com.mkt.risk.response;

import java.time.Instant;

public record RiskRuleResponse(
        String ruleCode, boolean enabled, long threshold, Long windowSeconds, String action, Instant updatedAt) {}
