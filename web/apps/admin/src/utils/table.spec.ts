import { describe, expect, it } from "vitest";
import { zhCN } from "@/locales/zh-CN";
import { adminPagination, adminRowKey } from "./table";

describe("adminRowKey", () => {
  it("prefers id then code then JSON", () => {
    expect(adminRowKey({ id: 12, code: "A" })).toBe("id:12");
    expect(adminRowKey({ code: "A" })).toBe("code:A");
    expect(adminRowKey({ period: "2026-08-20", dimKey: "R-a" })).toBe(
      '{"period":"2026-08-20","dimKey":"R-a"}',
    );
  });
});

describe("adminPagination", () => {
  it("binds current pageSize total for Ant Design Table", () => {
    const pagination = adminPagination(2, 20, 41);
    expect(pagination.current).toBe(2);
    expect(pagination.pageSize).toBe(20);
    expect(pagination.total).toBe(41);
    expect(pagination.size).toBe("small");
    expect(pagination.showSizeChanger).toBe(false);
    expect(pagination.hideOnSinglePage).toBe(false);
    expect(pagination.showTotal?.(41, [21, 40])).toBe(`${zhCN.common.total} 41`);
  });
});

