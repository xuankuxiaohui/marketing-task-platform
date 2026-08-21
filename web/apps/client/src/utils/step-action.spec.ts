import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { actionHref, actionRouteName, stepActionUi } from "./step-action";

describe("stepActionUi", () => {
  it("uses click copy for CLICK steps even when action is NONE", () => {
    expect(stepActionUi({ type: "CLICK", action: { actionType: "NONE" } })).toEqual({
      mode: "click",
      buttonText: zhCN.task.goComplete,
      disabled: false,
    });
  });

  it("shows go-external then wait-confirm for CALLBACK", () => {
    expect(
      stepActionUi({ type: "CALLBACK", action: { actionType: "LINK", params: { url: "https://ex.test" } } }),
    ).toEqual({
      mode: "callback-go",
      buttonText: zhCN.task.goExternal,
      disabled: false,
    });
    expect(
      stepActionUi(
        { type: "CALLBACK", action: { actionType: "LINK", params: { url: "https://ex.test" } } },
        true,
      ),
    ).toEqual({
      mode: "callback-wait",
      buttonText: zhCN.task.waitConfirm,
      disabled: true,
    });
    expect(stepActionUi({ type: "CALLBACK", action: { actionType: "NONE" } }).mode).toBe("callback-wait");
  });

  it("treats PROGRESS as non-clickable and prefers custom buttonText", () => {
    expect(stepActionUi({ type: "PROGRESS" }).mode).toBe("progress");
    expect(stepActionUi({ type: "CLICK", action: { actionType: "ROUTE", buttonText: "去浏览" } }).buttonText).toBe(
      "去浏览",
    );
  });
});

describe("actionHref", () => {
  it("only opens https links and scheme strings", () => {
    expect(actionHref({ actionType: "LINK", params: { url: "https://ex.test/a" } })).toBe("https://ex.test/a");
    expect(actionHref({ actionType: "LINK", params: { url: "http://ex.test/a" } })).toBeUndefined();
    expect(actionHref({ actionType: "SCHEME", params: { scheme: "app://task" } })).toBe("app://task");
    expect(actionRouteName({ actionType: "ROUTE", params: { route: "home" } })).toBe("home");
  });
});
