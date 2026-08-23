import type { CalendarDayView } from "@/api/signin";

const WEEKDAY = ["日", "一", "二", "三", "四", "五", "六"] as const;

export type HomeWeekCell = {
  date: string;
  weekday: string;
  dayNum: number;
  state: string;
};

export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function shiftIsoDate(iso: string, delta: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const next = new Date(Date.UTC(y, m - 1, d + delta));
  const mm = String(next.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(next.getUTCDate()).padStart(2, "0");
  return `${next.getUTCFullYear()}-${mm}-${dd}`;
}

export function homeSigninWeek(days: CalendarDayView[], todayIso: string): HomeWeekCell[] {
  const byDate = new Map(days.map((day) => [day.date.slice(0, 10), day.state]));
  const cells: HomeWeekCell[] = [];
  for (let i = 6; i >= 0; i -= 1) {
    const date = shiftIsoDate(todayIso, -i);
    const [y, month, dayNum] = date.split("-").map(Number);
    const utc = new Date(Date.UTC(y, month - 1, dayNum));
    cells.push({
      date,
      weekday: WEEKDAY[utc.getUTCDay()] ?? "",
      dayNum,
      state: byDate.get(date) ?? "NONE",
    });
  }
  return cells;
}

export const WEEKDAYS = WEEKDAY;

export type MonthGridCell = {
  date: string;
  dayNum: number;
  inMonth: boolean;
  state: string;
};

export function yearMonthOf(iso: string): string {
  return iso.slice(0, 7);
}

export function shiftYearMonth(yearMonth: string, delta: number): string {
  const [y, m] = yearMonth.split("-").map(Number);
  const next = new Date(Date.UTC(y, m - 1 + delta, 1));
  const mm = String(next.getUTCMonth() + 1).padStart(2, "0");
  return `${next.getUTCFullYear()}-${mm}`;
}

export function monthGrid(yearMonth: string, days: CalendarDayView[]): MonthGridCell[] {
  const [y, m] = yearMonth.split("-").map(Number);
  const first = new Date(Date.UTC(y, m - 1, 1));
  const lastNum = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const lead = first.getUTCDay();
  const byDate = new Map(days.map((day) => [day.date.slice(0, 10), day.state]));
  const cells: MonthGridCell[] = [];
  for (let i = 0; i < lead; i += 1) {
    cells.push({ date: "", dayNum: 0, inMonth: false, state: "PAD" });
  }
  for (let dayNum = 1; dayNum <= lastNum; dayNum += 1) {
    const mm = String(m).padStart(2, "0");
    const dd = String(dayNum).padStart(2, "0");
    const date = `${y}-${mm}-${dd}`;
    cells.push({
      date,
      dayNum,
      inMonth: true,
      state: byDate.get(date) ?? "NONE",
    });
  }
  return cells;
}
