import { zhCN } from "@/locales/zh-CN";

export const ADMIN_PAGE_SIZE = 15;

const KEY_FIELDS = ["id", "code", "namespace", "eventCode", "userId", "prizeId", "hitLogId"] as const;

export function adminPagination(
  page: number,
  pageSize: number,
  total: number,
): {
  current: number;
  pageSize: number;
  total: number;
  size: "small";
  showSizeChanger: boolean;
  hideOnSinglePage: boolean;
  showTotal: (count: number) => string;
} {
  return {
    current: page,
    pageSize,
    total,
    size: "small",
    showSizeChanger: false,
    hideOnSinglePage: false,
    showTotal: (count) => `${zhCN.common.total} ${count}`,
  };
}

export function adminRowKey(record: unknown): string {
  if (record !== null && typeof record === "object") {
    const row = record as Record<string, unknown>;
    for (const field of KEY_FIELDS) {
      if (row[field] != null && row[field] !== "") {
        return `${field}:${String(row[field])}`;
      }
    }
  }
  try {
    return JSON.stringify(record);
  } catch {
    return "row";
  }
}
