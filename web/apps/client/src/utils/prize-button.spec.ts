import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { FULFILL_STATUSES, GRANT_STATUSES, prizeButtonState } from "./prize-button";

describe("prizeButtonState", () => {
  it("maps R35.2 claimable / loading / retry / pending / failed / expired", () => {
    expect(prizeButtonState({ status: "WON" })).toEqual({
      kind: "claim",
      label: zhCN.prize.claim,
      disabled: false,
      loading: false,
      countdown: true,
      contact: false,
    });
    expect(prizeButtonState({ status: "CLAIMING" })).toMatchObject({
      kind: "loading",
      label: zhCN.prize.claiming,
      disabled: true,
      loading: true,
    });
    expect(prizeButtonState({ status: "RETRY_PENDING", failReason: "STOCK_SHORT" })).toMatchObject({
      kind: "retry",
      label: zhCN.prize.retry,
      disabled: false,
      reason: "STOCK_SHORT",
    });
    expect(prizeButtonState({ status: "PENDING" })).toMatchObject({
      kind: "terminal",
      label: zhCN.prize.creating,
      disabled: true,
    });
    expect(prizeButtonState({ status: "PERMANENT_FAILED", failReason: "PRIZE_DISABLED" })).toMatchObject({
      kind: "terminal",
      label: "PRIZE_DISABLED",
      disabled: true,
      contact: true,
    });
    expect(prizeButtonState({ status: "EXPIRED" })).toMatchObject({
      kind: "terminal",
      label: zhCN.prize.expired,
      disabled: true,
    });
  });

  it("maps GRANTED by fulfillment four-state (R35.2)", () => {
    expect(prizeButtonState({ status: "GRANTED", fulfillmentStatus: "ARRIVED" })).toMatchObject({
      kind: "terminal",
      label: zhCN.prize.arrived,
      disabled: true,
    });
    expect(prizeButtonState({ status: "GRANTED", fulfillmentStatus: "SENDING" })).toMatchObject({
      kind: "terminal",
      label: zhCN.prize.sending,
      disabled: true,
    });
    expect(prizeButtonState({ status: "GRANTED", fulfillmentStatus: "FULFILL_FAILED", fulfillFailReason: "TIMEOUT" })).toMatchObject({
      kind: "terminal",
      label: "TIMEOUT",
      disabled: true,
      contact: true,
    });
    expect(prizeButtonState({ status: "GRANTED", fulfillmentStatus: "NONE" })).toMatchObject({
      kind: "terminal",
      label: zhCN.prize.arrived,
      disabled: true,
    });
  });

  it("locks seven grant states × four fulfillment states to one button mapping", () => {
    const table: Record<string, { kind: string; disabled: boolean }> = {};
    for (const status of GRANT_STATUSES) {
      for (const fulfillmentStatus of FULFILL_STATUSES) {
        const state = prizeButtonState({ status, fulfillmentStatus });
        table[`${status}/${fulfillmentStatus}`] = { kind: state.kind, disabled: state.disabled };
      }
    }
    for (const fulfillment of FULFILL_STATUSES) {
      expect(table[`WON/${fulfillment}`]).toEqual({ kind: "claim", disabled: false });
      expect(table[`CLAIMING/${fulfillment}`]).toEqual({ kind: "loading", disabled: true });
      expect(table[`RETRY_PENDING/${fulfillment}`]).toEqual({ kind: "retry", disabled: false });
      expect(table[`PENDING/${fulfillment}`]).toEqual({ kind: "terminal", disabled: true });
      expect(table[`PERMANENT_FAILED/${fulfillment}`]).toEqual({ kind: "terminal", disabled: true });
      expect(table[`EXPIRED/${fulfillment}`]).toEqual({ kind: "terminal", disabled: true });
    }
    expect(table["GRANTED/ARRIVED"]).toEqual({ kind: "terminal", disabled: true });
    expect(table["GRANTED/NONE"]).toEqual({ kind: "terminal", disabled: true });
    expect(table["GRANTED/SENDING"]).toEqual({ kind: "terminal", disabled: true });
    expect(table["GRANTED/FULFILL_FAILED"]).toEqual({ kind: "terminal", disabled: true });
    expect(prizeButtonState({ status: "GRANTED", fulfillmentStatus: "ARRIVED" }).label).toBe(zhCN.prize.arrived);
    expect(prizeButtonState({ status: "GRANTED", fulfillmentStatus: "SENDING" }).label).toBe(zhCN.prize.sending);
    expect(prizeButtonState({ status: "GRANTED", fulfillmentStatus: "FULFILL_FAILED" }).label).toBe(zhCN.prize.sendFailed);
  });
});
