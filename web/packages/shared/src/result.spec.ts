import { describe, expect, it } from "vitest";
import { isFail, isOk, type Result } from "./result";

describe("Result guards", () => {
  it("isOk only when code is number 0", () => {
    const ok: Result<{ n: number }> = { code: 0, message: "ok", data: { n: 1 } };
    expect(isOk(ok)).toBe(true);
    if (isOk(ok)) {
      expect(ok.data?.n).toBe(1);
    }
    expect(isOk({ code: "0", message: "ok" })).toBe(false);
    expect(isOk(undefined)).toBe(false);
  });

  it("isFail only when code is a string", () => {
    const fail: Result = { code: "auth.login.invalid-credential", message: "用户名或密码错误" };
    expect(isFail(fail)).toBe(true);
    if (isFail(fail)) {
      expect(fail.code).toBe("auth.login.invalid-credential");
    }
    expect(isFail({ code: 0, message: "ok" })).toBe(false);
  });
});
