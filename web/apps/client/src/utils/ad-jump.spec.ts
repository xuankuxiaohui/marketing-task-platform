import { describe, expect, it } from "vitest";
import { resolveAdJump } from "./ad-jump";

describe("resolveAdJump", () => {
  it("maps closed jump types", () => {
    expect(resolveAdJump({ jumpType: "NONE" })).toBeUndefined();
    expect(resolveAdJump({ jumpType: "LINK", jumpParams: { url: "https://example.com/a" } })).toEqual({
      kind: "link",
      target: "https://example.com/a",
    });
    expect(resolveAdJump({ jumpType: "ROUTE", jumpParams: { route: "task-detail", taskId: 3 } })).toEqual({
      kind: "route",
      target: "/task/3",
    });
    expect(resolveAdJump({ jumpType: "LINK", jumpParams: { url: "http://insecure" } })).toBeUndefined();
  });
});
