import { describe, expect, it } from "vitest";
import { FileImageOutlined, SafetyCertificateOutlined, UserOutlined } from "@ant-design/icons-vue";
import { iconForMenuRoute } from "./sidebar-icons";

describe("iconForMenuRoute", () => {
  it("maps known submenu routes to distinct icons", () => {
    expect(iconForMenuRoute("/system/users")).toBe(UserOutlined);
    expect(iconForMenuRoute("/system/roles")).toBe(SafetyCertificateOutlined);
    expect(iconForMenuRoute("/ad/materials")).toBe(FileImageOutlined);
  });

  it("does not reuse the parent-only mapping for a longer sibling path", () => {
    expect(iconForMenuRoute("/system/users")).not.toBe(iconForMenuRoute("/system/roles"));
    expect(iconForMenuRoute("/ad/positions")).not.toBe(iconForMenuRoute("/ad/materials"));
  });
});
