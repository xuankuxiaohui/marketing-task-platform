package com.mkt.risk.domain;

import com.mkt.contract.RiskAction;
import com.mkt.kernel.BusinessException;
import com.mkt.risk.support.RiskErrorCodes;

/** Appendix A legal ranges for R-a–R-f (R26.6). */
public final class RiskRuleRanges {

    private RiskRuleRanges() {}

    public static void assertValid(RiskRuleCode code, long threshold, Long windowSeconds, RiskAction action) {
        if (code == null || action == null || action == RiskAction.PASS) {
            throw new BusinessException(RiskErrorCodes.RULE_RANGE_VIOLATED);
        }
        switch (code) {
            case RA -> {
                assertThreshold(threshold, 1, 10_000);
                assertWindow(windowSeconds, 60, 86_400);
            }
            case RB -> {
                assertThreshold(threshold, 1, 10_000);
                assertWindow(windowSeconds, 3_600, 604_800);
            }
            case RC -> {
                assertThreshold(threshold, 2, 1_000);
                assertWindow(windowSeconds, 60, 86_400);
            }
            case RD -> {
                assertThreshold(threshold, 2, 1_000);
                assertWindow(windowSeconds, 3_600, 604_800);
            }
            case RE -> {
                assertThreshold(threshold, 1, 3_600);
                if (windowSeconds != null) {
                    throw new BusinessException(RiskErrorCodes.RULE_RANGE_VIOLATED);
                }
            }
            case RF -> {
                assertThreshold(threshold, 5, 10_000);
                assertWindow(windowSeconds, 10, 3_600);
            }
        }
    }

    private static void assertThreshold(long threshold, long min, long max) {
        if (threshold < min || threshold > max) {
            throw new BusinessException(RiskErrorCodes.RULE_RANGE_VIOLATED);
        }
    }

    private static void assertWindow(Long windowSeconds, long min, long max) {
        if (windowSeconds == null || windowSeconds < min || windowSeconds > max) {
            throw new BusinessException(RiskErrorCodes.RULE_RANGE_VIOLATED);
        }
    }
}
