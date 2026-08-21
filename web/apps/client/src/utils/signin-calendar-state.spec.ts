import { describe, expect, it } from "vitest";
import { consecutiveDays, signinCalendarCell, type SigninCalendarRecord } from "./signin-calendar-state";

/** R36.1 SigninCalendarStateTest: calendar cells match the record set. */
describe("SigninCalendarStateTest", () => {
  const today = "2026-08-20";
  const records: SigninCalendarRecord[] = [
    { signDate: "2026-08-18", source: "CHECKIN" },
    { signDate: "2026-08-17", source: "CATCHUP" },
  ];
  const base = {
    today,
    records,
    windowDays: 7,
    activityStart: "2026-08-01",
    activityEnd: "2026-08-31",
  };

  it("maps signed / catchup / missed-catchable / today", () => {
    expect(signinCalendarCell({ ...base, date: "2026-08-18" })).toBe("SIGNED");
    expect(signinCalendarCell({ ...base, date: "2026-08-17" })).toBe("CATCHUP");
    expect(signinCalendarCell({ ...base, date: "2026-08-19" })).toBe("MISSED_CATCHABLE");
    expect(signinCalendarCell({ ...base, date: today })).toBe("TODAY_AVAILABLE");
  });

  it("greys future, out-of-window, and old misses", () => {
    expect(signinCalendarCell({ ...base, date: "2026-08-21" })).toBe("NONE");
    expect(signinCalendarCell({ ...base, date: "2026-08-10" })).toBe("NONE");
    expect(signinCalendarCell({ ...base, date: "2026-07-31" })).toBe("NONE");
  });

  it("random sign/catchup sequences stay consistent with records", () => {
    const seq: SigninCalendarRecord[] = [];
    const days = ["2026-08-13", "2026-08-14", "2026-08-15", "2026-08-16", "2026-08-17", "2026-08-18", "2026-08-19"];
    for (let i = 0; i < days.length; i += 1) {
      if (i % 3 === 2) {
        continue;
      }
      seq.push({ signDate: days[i], source: i % 2 === 0 ? "CHECKIN" : "CATCHUP" });
    }
    for (const day of days) {
      const state = signinCalendarCell({ ...base, records: seq, date: day, today: "2026-08-20" });
      const hit = seq.find((row) => row.signDate === day);
      if (hit) {
        expect(state).toBe(hit.source === "CATCHUP" ? "CATCHUP" : "SIGNED");
      } else {
        expect(state).toBe("MISSED_CATCHABLE");
      }
    }
  });

  it("computes live streak including yesterday until today is signed", () => {
    expect(consecutiveDays(["2026-08-19", "2026-08-18"], "2026-08-20")).toBe(2);
    expect(consecutiveDays(["2026-08-20", "2026-08-19", "2026-08-18"], "2026-08-20")).toBe(3);
    expect(consecutiveDays(["2026-08-18"], "2026-08-20")).toBe(0);
  });
});
