import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { SpendingStatisticsPanel } from "./spending-statistics-panel";

const monthlyStatistics = {
  period: "MONTH",
  referenceDate: "2026-07-26",
  startDate: "2026-07-01",
  endDateExclusive: "2026-08-01",
  current: { totalAmount: 125_000, transactionCount: 4 },
  previous: { totalAmount: 100_000, transactionCount: 3 },
  amountChange: 25_000,
  changeRatePercent: 25.0,
  byPayer: [{ key: "1", label: "나", amount: 125_000, transactionCount: 4 }],
  byPaymentMethod: [
    { key: "CARD", label: "카드", amount: 125_000, transactionCount: 4 },
  ],
  byCategory: [{ key: "식비", label: "식비", amount: 125_000, transactionCount: 4 }],
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("SpendingStatisticsPanel", () => {
  test("shows a period summary and reloads it when the period changes", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(monthlyStatistics))
      .mockResolvedValueOnce(
        jsonResponse({
          ...monthlyStatistics,
          period: "DAY",
          startDate: "2026-07-26",
          endDateExclusive: "2026-07-27",
          current: { totalAmount: 8_000, transactionCount: 1 },
          amountChange: -2_000,
          changeRatePercent: -20.0,
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<SpendingStatisticsPanel refreshKey={0} />);

    const totalCard = (await screen.findByText("총지출")).parentElement;
    expect(totalCard).not.toBeNull();
    expect(within(totalCard as HTMLElement).getByText("125,000원")).toBeDefined();
    expect(screen.getByText("25,000원 증가 (25%)")).toBeDefined();
    expect(screen.getByRole("heading", { name: "카테고리" })).toBeDefined();

    await user.click(screen.getByRole("button", { name: "일간" }));

    const reloadedTotalCard = (await screen.findByText("총지출")).parentElement;
    expect(within(reloadedTotalCard as HTMLElement).getByText("8,000원")).toBeDefined();
    expect(fetchMock.mock.calls[1][0]).toContain("period=DAY");
  });

  test("shows an explicit empty state", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        jsonResponse({
          ...monthlyStatistics,
          current: { totalAmount: 0, transactionCount: 0 },
          previous: { totalAmount: 0, transactionCount: 0 },
          amountChange: 0,
          changeRatePercent: null,
          byPayer: [],
          byPaymentMethod: [],
          byCategory: [],
        }),
      ),
    );

    render(<SpendingStatisticsPanel refreshKey={0} />);

    expect(
      await screen.findByText("이 기간에는 기록된 지출이 없습니다."),
    ).toBeDefined();
    expect(screen.getByText("변화 없음")).toBeDefined();
  });
});

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
