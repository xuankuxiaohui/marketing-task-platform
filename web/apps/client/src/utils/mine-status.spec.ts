import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { resolveMineStatus } from "./mine-status";

describe("resolveMineStatus", () => {
  it("keeps closed instance enums and maps Vant index, title, or alias to COMPLETED", () => {
    expect(resolveMineStatus("COMPLETED")).toBe("COMPLETED");
    expect(resolveMineStatus("completed")).toBe("COMPLETED");
    expect(resolveMineStatus(1)).toBe("COMPLETED");
    expect(resolveMineStatus("1")).toBe("COMPLETED");
    expect(resolveMineStatus("SUCCESS")).toBe("COMPLETED");
    expect(resolveMineStatus("DONE")).toBe("COMPLETED");
    expect(resolveMineStatus(zhCN.task.completed)).toBe("COMPLETED");
    expect(resolveMineStatus("IN_PROGRESS")).toBe("IN_PROGRESS");
    expect(resolveMineStatus(0)).toBe("IN_PROGRESS");
    expect(resolveMineStatus(zhCN.task.inProgress)).toBe("IN_PROGRESS");
    expect(resolveMineStatus("ABANDONED")).toBe("ABANDONED");
    expect(resolveMineStatus(2)).toBe("ABANDONED");
    expect(resolveMineStatus("EXPIRED")).toBe("EXPIRED");
    expect(resolveMineStatus(3)).toBe("EXPIRED");
  });

  it("drops blank or unknown values so the API is not queried with a fake status", () => {
    expect(resolveMineStatus(undefined)).toBeUndefined();
    expect(resolveMineStatus("")).toBeUndefined();
    expect(resolveMineStatus("HISTORY")).toBeUndefined();
    expect(resolveMineStatus(9)).toBeUndefined();
  });
});
