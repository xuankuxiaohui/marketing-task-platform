package com.mkt.risk.domain;

/** Sliding-window counts already observed; {@code null} means that rule is not evaluated. */
public record WindowCounts(Long ra, Long rb, Long rc, Long rd, Long rf) {

    public static WindowCounts empty() {
        return new WindowCounts(null, null, null, null, null);
    }

    public Long of(RiskRuleCode code) {
        return switch (code) {
            case RA -> ra;
            case RB -> rb;
            case RC -> rc;
            case RD -> rd;
            case RF -> rf;
            case RE -> null;
        };
    }
}
