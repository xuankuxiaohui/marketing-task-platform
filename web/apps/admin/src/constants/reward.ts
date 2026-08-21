/** Closed enums from design §4.5 / domain types. Do not invent values. */

export const PRIZE_STATUS = {
  DRAFT: "DRAFT",
  ENABLED: "ENABLED",
  DISABLED: "DISABLED",
} as const;

export const CLAIM_MODES = ["AUTO", "MANUAL"] as const;

export const REWARD_TARGETS = ["PLATFORM", "THIRD_PARTY"] as const;

export const FULFILLMENT_MODES = ["INSTANT", "ASYNC"] as const;

export const COST_MODES = ["NONE", "FIXED_UNIT", "FACE_VALUE"] as const;

export const RECON_POLICIES = ["REVIEW", "AUTO"] as const;

export const GRANT_STATUS = [
  "PENDING",
  "WON",
  "CLAIMING",
  "GRANTED",
  "RETRY_PENDING",
  "PERMANENT_FAILED",
  "EXPIRED",
] as const;

export const FULFILL_STATUS = ["NONE", "SENDING", "ARRIVED", "FULFILL_FAILED"] as const;

export const RECON_RESULTS = ["MATCHED", "PLATFORM_ONLY", "CHANNEL_ONLY", "AMOUNT_MISMATCH"] as const;

export const RECON_ACTIONS = ["REFULFILL", "MANUAL_GRANT", "ABSORB", "LEDGER_ONLY"] as const;

export const RECON_REVIEW = {
  NONE: "NONE",
  PENDING_REVIEW: "PENDING_REVIEW",
  CONFIRMED: "CONFIRMED",
  REJECTED: "REJECTED",
} as const;

export const BYPASS_RULES = ["REGION", "LEVEL", "TAG"] as const;

export const POINT_TYPES = ["EARN", "CONSUME", "EXPIRE", "ADJUST", "REVERSAL"] as const;
