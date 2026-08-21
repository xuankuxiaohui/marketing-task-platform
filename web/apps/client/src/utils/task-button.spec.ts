import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { taskButtonState } from "./task-button";

describe("taskButtonState", () => {
  it("maps list userStatus to R34.2 button copy and disabled flags", () => {
    expect(taskButtonState("NOT_STARTED")).toEqual({
      kind: "claim",
      label: zhCN.task.claim,
      disabled: false,
    });
    expect(taskButtonState("IN_PROGRESS")).toEqual({
      kind: "continue",
      label: zhCN.task.continue,
      disabled: false,
    });
    expect(taskButtonState("COMPLETED")).toEqual({
      kind: "terminal",
      label: zhCN.task.completed,
      disabled: true,
    });
    expect(taskButtonState("ABANDONED")).toEqual({
      kind: "terminal",
      label: zhCN.task.abandoned,
      disabled: true,
    });
    expect(taskButtonState("EXPIRED")).toEqual({
      kind: "terminal",
      label: zhCN.task.expired,
      disabled: true,
    });
  });

  it("treats unknown and offline as ended terminal copy", () => {
    expect(taskButtonState("OFFLINE")).toEqual({
      kind: "terminal",
      label: zhCN.task.ended,
      disabled: true,
    });
    expect(taskButtonState(undefined).disabled).toBe(true);
  });
});
