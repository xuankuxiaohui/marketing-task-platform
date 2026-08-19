package com.mkt.risk.domain;

/** Closed built-in rule set (R26.1). */
public enum RiskRuleCode {
    RA("R-a"),
    RB("R-b"),
    RC("R-c"),
    RD("R-d"),
    RE("R-e"),
    RF("R-f");

    private final String code;

    RiskRuleCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static RiskRuleCode fromCode(String code) {
        for (RiskRuleCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("unknown rule: " + code);
    }
}
