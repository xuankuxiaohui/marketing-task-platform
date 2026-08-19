package com.mkt.contract;

/** Risk check result; the port does not throw business exceptions (design §2.2.3). */
public record RiskVerdict(RiskAction action) {

    public RiskVerdict {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
    }
}
