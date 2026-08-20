import { zhCN } from "@/locales/zh-CN";

export const GRANT_STATUSES = [
  "PENDING",
  "WON",
  "CLAIMING",
  "GRANTED",
  "RETRY_PENDING",
  "PERMANENT_FAILED",
  "EXPIRED",
] as const;

export const FULFILL_STATUSES = ["NONE", "SENDING", "ARRIVED", "FULFILL_FAILED"] as const;

export type GrantStatus = (typeof GRANT_STATUSES)[number];
export type FulfillStatus = (typeof FULFILL_STATUSES)[number];

export type PrizeButtonKind = "claim" | "loading" | "retry" | "terminal";

export type PrizeButtonState = {
  kind: PrizeButtonKind;
  label: string;
  disabled: boolean;
  loading: boolean;
  countdown: boolean;
  reason?: string;
  contact: boolean;
};

export type PrizeButtonInput = {
  status?: string;
  fulfillmentStatus?: string;
  failReason?: string;
  fulfillFailReason?: string;
};

function reasonOf(value?: string): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export function prizeButtonState(input: PrizeButtonInput): PrizeButtonState {
  const status = input.status;
  switch (status) {
    case "WON":
      return {
        kind: "claim",
        label: zhCN.prize.claim,
        disabled: false,
        loading: false,
        countdown: true,
        contact: false,
      };
    case "CLAIMING":
      return {
        kind: "loading",
        label: zhCN.prize.claiming,
        disabled: true,
        loading: true,
        countdown: false,
        contact: false,
      };
    case "RETRY_PENDING":
      return {
        kind: "retry",
        label: zhCN.prize.retry,
        disabled: false,
        loading: false,
        countdown: false,
        reason: reasonOf(input.failReason),
        contact: false,
      };
    case "PENDING":
      return {
        kind: "terminal",
        label: zhCN.prize.creating,
        disabled: true,
        loading: false,
        countdown: false,
        contact: false,
      };
    case "PERMANENT_FAILED":
      return {
        kind: "terminal",
        label: reasonOf(input.failReason) ?? zhCN.prize.failed,
        disabled: true,
        loading: false,
        countdown: false,
        reason: reasonOf(input.failReason),
        contact: true,
      };
    case "EXPIRED":
      return {
        kind: "terminal",
        label: zhCN.prize.expired,
        disabled: true,
        loading: false,
        countdown: false,
        contact: false,
      };
    case "GRANTED":
      return grantedState(input.fulfillmentStatus, input.fulfillFailReason);
    default:
      return {
        kind: "terminal",
        label: zhCN.prize.failed,
        disabled: true,
        loading: false,
        countdown: false,
        contact: false,
      };
  }
}

function grantedState(fulfillmentStatus?: string, fulfillFailReason?: string): PrizeButtonState {
  switch (fulfillmentStatus) {
    case "SENDING":
      return {
        kind: "terminal",
        label: zhCN.prize.sending,
        disabled: true,
        loading: false,
        countdown: false,
        contact: false,
      };
    case "FULFILL_FAILED":
      return {
        kind: "terminal",
        label: reasonOf(fulfillFailReason) ?? zhCN.prize.sendFailed,
        disabled: true,
        loading: false,
        countdown: false,
        reason: reasonOf(fulfillFailReason),
        contact: true,
      };
    case "ARRIVED":
    case "NONE":
    default:
      return {
        kind: "terminal",
        label: zhCN.prize.arrived,
        disabled: true,
        loading: false,
        countdown: false,
        contact: false,
      };
  }
}

export function isPendingPrizeTab(input: PrizeButtonInput): boolean {
  const status = input.status;
  if (status === "WON" || status === "CLAIMING" || status === "RETRY_PENDING") {
    return true;
  }
  return status === "GRANTED" && (input.fulfillmentStatus === "SENDING" || input.fulfillmentStatus === "FULFILL_FAILED");
}
