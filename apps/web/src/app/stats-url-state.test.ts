import { describe, expect, test } from "vitest";
import {
  calendarReturnUrl,
  dailyStatsUrl,
  normalizeStatsUrlState,
  statsUrl,
  todayInSeoul,
} from "./stats-url-state";

describe("stats URL state", () => {
  test("normalizes valid URL values", () => {
    expect(
      normalizeStatsUrlState(
        {
          calendar: "open",
          date: "2026-07-26",
          payer: "partner",
          period: "month",
        },
        "2026-08-31",
      ),
    ).toEqual({
      calendarExpanded: true,
      payer: "PARTNER",
      period: "MONTH",
      referenceDate: "2026-07-26",
    });
  });

  test("falls back from invalid URL values", () => {
    expect(
      normalizeStatsUrlState(
        { calendar: "yes", date: "2026-02-30", payer: "someone", period: "year" },
        "2026-08-31",
      ),
    ).toEqual({
      calendarExpanded: false,
      payer: "ALL",
      period: "MONTH",
      referenceDate: "2026-08-31",
    });
  });

  test("builds stable statistics and daily detail URLs", () => {
    expect(
      statsUrl({
        calendarExpanded: true,
        payer: "ME",
        period: "MONTH",
        referenceDate: "2026-07-26",
      }),
    ).toBe("/stats?period=MONTH&payer=ME&date=2026-07-26&calendar=open");
    expect(dailyStatsUrl("2026-07-20", "ME", "2026-07-26")).toBe(
      "/stats/daily/2026-07-20?payer=ME&statsDate=2026-07-26",
    );
    expect(calendarReturnUrl("ME", "2026-07-26")).toBe(
      "/stats?period=MONTH&payer=ME&date=2026-07-26&calendar=open",
    );
  });

  test("formats today using the Seoul date", () => {
    expect(todayInSeoul(new Date("2026-08-30T16:00:00Z"))).toBe("2026-08-31");
  });
});
