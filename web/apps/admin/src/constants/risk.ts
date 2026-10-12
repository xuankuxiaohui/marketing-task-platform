/** Closed enums from design §4.6 / V4. Do not invent values. */

export const RISK_DIMENSIONS = ["USER", "IP", "DEVICE"] as const;

export const RISK_LIST_TYPES = ["BLACK", "WHITE"] as const;

export const RISK_RULE_CODES = ["R-a", "R-b", "R-c", "R-d", "R-e", "R-f"] as const;

export const RISK_HIT_TYPES = ["RULE", "LIST"] as const;

export const RISK_ACTION_RESULTS = ["REJECTED", "SILENT_REJECTED", "MARKED", "FROZEN"] as const;

export const RISK_HANDLE_ACTIONS = ["ADD_BLACK", "REMOVE_BLACK", "MARK_FALSE_POSITIVE"] as const;

/**
 * Admin copy for risk-rule cache lag must use this N.
 * Source of truth: server/domain-risk/src/main/java/com/mkt/risk/application/MybatisRiskRuleConfigStore.java
 *   private static final Duration LIST_ALL_TTL = Duration.ofSeconds(3);
 * Do not invent or change TTL here — capacity frozen; UX-5 is copy only.
 */
export const RISK_RULE_LIST_CACHE_TTL_SECONDS = 3 as const;
