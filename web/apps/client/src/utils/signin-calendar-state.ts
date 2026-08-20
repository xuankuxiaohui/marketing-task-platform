/** R36.1 calendar cell states. Mirrors backend CalendarCellState. */
export const CALENDAR_STATES = [
  "SIGNED",
  "CATCHUP",
  "MISSED_CATCHABLE",
  "TODAY_AVAILABLE",
  "NONE",
] as const;

export type CalendarCellState = (typeof CALENDAR_STATES)[number];

export type SigninCalendarRecord = {
  signDate: string;
  source: "CHECKIN" | "CATCHUP" | string;
};

export type SigninCalendarCellInput = {
  date: string;
  today: string;
  records: SigninCalendarRecord[];
  windowDays: number;
  activityStart?: string;
  activityEnd?: string;
};

function inActivity(day: string, start?: string, end?: string): boolean {
  if (start && day < start) {
    return false;
  }
  return !end || day <= end;
}

function shiftDays(isoDate: string, delta: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const utc = Date.UTC(y, m - 1, d + delta);
  const next = new Date(utc);
  const mm = String(next.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(next.getUTCDate()).padStart(2, "0");
  return `${next.getUTCFullYear()}-${mm}-${dd}`;
}

export function signinCalendarCell(input: SigninCalendarCellInput): CalendarCellState {
  const hit = input.records.find((row) => row.signDate === input.date);
  if (hit) {
    return hit.source === "CATCHUP" ? "CATCHUP" : "SIGNED";
  }
  if (!inActivity(input.date, input.activityStart, input.activityEnd)) {
    return "NONE";
  }
  if (input.date === input.today) {
    return "TODAY_AVAILABLE";
  }
  const windowStart = shiftDays(input.today, -input.windowDays);
  if (input.date < input.today && input.date >= windowStart) {
    return "MISSED_CATCHABLE";
  }
  return "NONE";
}

export function consecutiveDays(signedDates: string[], today: string): number {
  const set = new Set(signedDates);
  const start = set.has(today) ? today : shiftDays(today, -1);
  if (!set.has(start)) {
    return 0;
  }
  let n = 0;
  let cursor = start;
  while (set.has(cursor)) {
    n += 1;
    cursor = shiftDays(cursor, -1);
  }
  return n;
}
