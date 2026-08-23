import { zhCN } from "@/locales/zh-CN";

export function adminStatusLabel(status: string | undefined | null): string {
  if (status == null || status === "") {
    return "";
  }
  const mapped = (zhCN.status as Record<string, string>)[status];
  return mapped ?? status;
}

export function adminStatusTone(
  status: string | undefined | null,
): "success" | "warning" | "info" | "danger" {
  switch (status) {
    case "ENABLED":
    case "PUBLISHED":
    case "COMPLETED":
    case "GRANTED":
    case "ARRIVED":
    case "MATCHED":
    case "CONFIRMED":
    case "SIGNED":
      return "success";
    case "SCHEDULED":
    case "IN_PROGRESS":
    case "SENDING":
    case "CLAIMING":
    case "PENDING":
    case "WON":
    case "RETRY_PENDING":
    case "PENDING_REVIEW":
      return "warning";
    case "PERMANENT_FAILED":
    case "FULFILL_FAILED":
    case "REJECTED":
    case "AMOUNT_MISMATCH":
    case "EXPIRED":
      return "danger";
    default:
      return "info";
  }
}

export function adminStatusClass(status: string | undefined | null): string {
  switch (adminStatusTone(status)) {
    case "success":
      return "status-tag--on";
    case "warning":
      return "status-tag--wait";
    case "danger":
      return "status-tag--bad";
    default:
      return "status-tag--off";
  }
}
