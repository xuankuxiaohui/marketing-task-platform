import { describe, expect, it } from "vitest";
import { buildConfigUpdateBody, CONFIG_MASK_DISPLAY } from "./config-update";

describe("buildConfigUpdateBody R8.2", () => {
  it("omits value when masked display is left unchanged", () => {
    const body = buildConfigUpdateBody(
      { configValue: CONFIG_MASK_DISPLAY, masked: true },
      { configGroup: "auth", status: "ENABLED", remark: "keep", value: CONFIG_MASK_DISPLAY },
    );
    expect(body).toEqual({ configGroup: "auth", status: "ENABLED", remark: "keep" });
    expect(body).not.toHaveProperty("value");
  });

  it("omits value when the field is cleared", () => {
    const body = buildConfigUpdateBody({ configValue: CONFIG_MASK_DISPLAY, masked: true }, { value: "" });
    expect(body).not.toHaveProperty("value");
  });

  it("sends a new secret when the operator types one", () => {
    const body = buildConfigUpdateBody(
      { configValue: CONFIG_MASK_DISPLAY, masked: true },
      { value: "NewSecret-1" },
    );
    expect(body.value).toBe("NewSecret-1");
  });

  it("sends value for unmasked configs", () => {
    const body = buildConfigUpdateBody({ configValue: "5", masked: false }, { value: "8" });
    expect(body.value).toBe("8");
  });
});
