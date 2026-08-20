import { describe, expect, it } from "vitest";
import { portalPasswordSatisfied } from "./password";

describe("portalPasswordSatisfied", () => {
  it("requires eight chars with letter and digit", () => {
    expect(portalPasswordSatisfied("abcdefg1")).toBe(true);
    expect(portalPasswordSatisfied("abcdefgh")).toBe(false);
    expect(portalPasswordSatisfied("12345678")).toBe(false);
    expect(portalPasswordSatisfied("abc1")).toBe(false);
  });
});
