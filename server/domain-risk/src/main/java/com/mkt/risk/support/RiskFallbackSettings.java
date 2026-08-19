package com.mkt.risk.support;

import org.springframework.stereotype.Component;

/** Mutable so ITs can flip {@code risk.fallback-policy} without identity ConfigService (task 24). */
@Component
public final class RiskFallbackSettings {

    private volatile RiskFallbackPolicy policy = RiskFallbackPolicy.ALLOW;

    public RiskFallbackPolicy policy() {
        return policy;
    }

    public void setPolicy(RiskFallbackPolicy policy) {
        this.policy = policy == null ? RiskFallbackPolicy.ALLOW : policy;
    }
}
