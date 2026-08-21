/** Closed enums from design §4.6 / V4. Do not invent values. */

export const RISK_DIMENSIONS = ["USER", "IP", "DEVICE"] as const;

export const RISK_LIST_TYPES = ["BLACK", "WHITE"] as const;

export const RISK_RULE_CODES = ["R-a", "R-b", "R-c", "R-d", "R-e", "R-f"] as const;

export const RISK_HIT_TYPES = ["RULE", "LIST"] as const;

export const RISK_ACTION_RESULTS = ["REJECTED", "SILENT_REJECTED", "MARKED", "FROZEN"] as const;

export const RISK_HANDLE_ACTIONS = ["ADD_BLACK", "REMOVE_BLACK", "MARK_FALSE_POSITIVE"] as const;
