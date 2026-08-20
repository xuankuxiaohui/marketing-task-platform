import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { sessionMessage } from "./session-reason";

describe("sessionMessage", () => {
  it("distinguishes concurrent kick from admin kick", () => {
    expect(sessionMessage("auth.session.kicked-concurrent")).toBe(zhCN.session.kickedConcurrent);
    expect(sessionMessage("auth.session.kicked-admin")).toBe(zhCN.session.kickedAdmin);
    expect(sessionMessage("auth.session.expired")).toBe(zhCN.session.expired);
    expect(sessionMessage("auth.session.missing")).toBe(zhCN.session.missing);
    expect(sessionMessage("auth.session.kicked-concurrent")).not.toBe(
      sessionMessage("auth.session.kicked-admin"),
    );
  });
});
