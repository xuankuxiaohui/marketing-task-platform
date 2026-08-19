package com.mkt.risk.domain;

/**
 * List-segment outcome (design §5.9). {@link #SKIP_RULES} skips R-a–R-f only; product
 * rules (claim limit, stock, mutex) still apply (R25.5).
 */
public enum ListDecision {
    REJECT,
    PASS,
    SKIP_RULES
}
