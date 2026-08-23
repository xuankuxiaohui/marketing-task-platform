import { describe, expect, it } from "vitest";
import { homeSigninWeek, monthGrid, shiftIsoDate, shiftYearMonth, toIsoDate } from "./home-week";

describe("homeSigninWeek", () => {
  it("builds seven days ending today and copies calendar states", () => {
    const cells = homeSigninWeek(
      [
        { date: "2026-08-20", state: "SIGNED" },
        { date: "2026-08-21", state: "SIGNED" },
        { date: "2026-08-22", state: "TODAY_AVAILABLE" },
      ],
      "2026-08-22",
    );
    expect(cells).toHaveLength(7);
    expect(cells[0]?.date).toBe("2026-08-16");
    expect(cells[6]?.date).toBe("2026-08-22");
    expect(cells[4]?.state).toBe("SIGNED");
    expect(cells[5]?.state).toBe("SIGNED");
    expect(cells[6]?.state).toBe("TODAY_AVAILABLE");
    expect(cells[1]?.state).toBe("NONE");
  });

  it("shifts ISO dates across month bounds", () => {
    expect(shiftIsoDate("2026-08-01", -1)).toBe("2026-07-31");
    expect(toIsoDate(new Date(2026, 7, 22))).toBe("2026-08-22");
  });
});

describe("monthGrid", () => {
  it("pads leading blanks and copies signed states for the month", () => {
    const cells = monthGrid("2026-08", [
      { date: "2026-08-01", state: "SIGNED" },
      { date: "2026-08-20", state: "TODAY_AVAILABLE" },
    ]);
    expect(cells[0]?.inMonth).toBe(false);
    const first = cells.find((cell) => cell.dayNum === 1);
    const twenty = cells.find((cell) => cell.dayNum === 20);
    expect(first?.state).toBe("SIGNED");
    expect(twenty?.state).toBe("TODAY_AVAILABLE");
    expect(shiftYearMonth("2026-01", -1)).toBe("2025-12");
    expect(shiftYearMonth("2026-12", 1)).toBe("2027-01");
  });
});
