/** Closed enums from design §4.4 / domain types. Do not invent values. */

export const DEFINITION_STATUS = {
  DRAFT: "DRAFT",
  SCHEDULED: "SCHEDULED",
  PUBLISHED: "PUBLISHED",
  OFFLINE: "OFFLINE",
} as const;

export const CYCLE_TYPES = ["NONE", "DAILY", "MONTHLY", "CRON", "SPECIAL"] as const;

export const STEP_TYPES = ["PASSIVE", "CLICK", "CALLBACK", "PROGRESS", "REWARD"] as const;

export const GRAY_TYPES = ["NONE", "RATIO", "AB", "CROWD"] as const;

export const INSTANCE_STATUS = {
  IN_PROGRESS: "IN_PROGRESS",
  COMPLETED: "COMPLETED",
  ABANDONED: "ABANDONED",
  EXPIRED: "EXPIRED",
} as const;

export const EXPR_TYPES = ["FILTER", "BRANCH"] as const;
