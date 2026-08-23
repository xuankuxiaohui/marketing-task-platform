import { afterEach, describe, expect, it, vi } from "vitest";
import { fail, ok } from "@/test-utils/result";
import { zhCN } from "@/locales/zh-CN";
import { formatWriteError, setWriteNotice, writeOrFeedback } from "./feedback";

describe("writeOrFeedback", () => {
  afterEach(() => {
    setWriteNotice(null);
  });

  it("notifies success on an ok Result", () => {
    const success = vi.fn();
    const error = vi.fn();
    setWriteNotice({ success, error });
    const parsed = writeOrFeedback(ok({ id: 9 }));
    expect(parsed.ok).toBe(true);
    if (parsed.ok) {
      expect(parsed.data).toEqual({ id: 9 });
    }
    expect(success).toHaveBeenCalledWith(zhCN.common.saved);
    expect(error).not.toHaveBeenCalled();
  });

  it("notifies message and traceId on a fail Result", () => {
    const success = vi.fn();
    const error = vi.fn();
    setWriteNotice({ success, error });
    const parsed = writeOrFeedback(fail("common.param-invalid", "权限 id 不合法", "trace-99"));
    expect(parsed.ok).toBe(false);
    if (!parsed.ok) {
      expect(parsed.feedback).toEqual({ message: "权限 id 不合法", traceId: "trace-99" });
    }
    expect(error).toHaveBeenCalledWith("权限 id 不合法 · trace-99");
    expect(success).not.toHaveBeenCalled();
  });
});

describe("formatWriteError", () => {
  it("appends traceId when present", () => {
    expect(formatWriteError({ message: "失败", traceId: "t-1" })).toBe("失败 · t-1");
    expect(formatWriteError({ message: "失败" })).toBe("失败");
  });
});
