import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { SpendingStatisticsPanel } from "./spending-statistics-panel";

const monthlyStatistics = {
  period: "MONTH",
  payer: "ALL",
  referenceDate: "2026-07-26",
  startDate: "2026-07-01",
  endDateExclusive: "2026-08-01",
  current: { totalAmount: 125_000, coupleLivingAmount: 90_000, childcareAmount: 35_000, transactionCount: 4 },
  previous: { totalAmount: 100_000, coupleLivingAmount: 80_000, childcareAmount: 20_000, transactionCount: 3 },
  amountChange: 25_000,
  changeRatePercent: 25.0,
  byPayer: [{ key: "1", label: "나", amount: 125_000, transactionCount: 4 }],
  byPaymentMethod: [
    { key: "CARD", label: "카드", amount: 125_000, transactionCount: 4 },
  ],
  byCategory: [{ key: "식비", label: "식비", amount: 125_000, transactionCount: 4 }],
  categoryComparisons: [
    {
      key: "FOOD",
      label: "식비",
      currentAmount: 125_000,
      currentTransactionCount: 4,
      previousAmount: 100_000,
      previousTransactionCount: 3,
      amountChange: 25_000,
      changeRatePercent: 25.0,
    },
  ],
  tagComparisons: [
    {
      key: "SUBSCRIPTION",
      label: "구독",
      currentAmount: 20_000,
      currentTransactionCount: 1,
      previousAmount: 15_000,
      previousTransactionCount: 1,
      amountChange: 5_000,
      changeRatePercent: 33.3,
    },
  ],
  recurringSpendingChanges: [
    {
      tag: "SUBSCRIPTION",
      label: "구독",
      direction: "INCREASED",
      currentAmount: 20_000,
      previousAmount: 15_000,
      amountChange: 5_000,
      message: "선택한 기간 구독 지출이 이전 기간보다 5,000원 증가했어요.",
    },
  ],
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
    expect(within(screen.getByText("부부 생활비").parentElement as HTMLElement).getByText("90,000원")).toBeDefined();
    expect(within(screen.getByText("육아비").parentElement as HTMLElement).getByText("35,000원")).toBeDefined();
    expect(screen.getByText("25,000원 증가 (25%)")).toBeDefined();
    expect(screen.getByRole("heading", { name: "카테고리 비교" })).toBeDefined();
    expect(screen.getByText("이전 100,000원 · 25,000원 증가 (25%)")).toBeDefined();
    expect(screen.getByRole("heading", { name: "태그 비교" })).toBeDefined();
    expect(
      screen.getByText("태그는 서로 겹칠 수 있어 합계가 총지출과 다를 수 있습니다."),
    ).toBeDefined();
    expect(screen.getByRole("heading", { name: "반복 지출 변화" })).toBeDefined();
    expect(
      screen.getByText("선택한 기간 구독 지출이 이전 기간보다 5,000원 증가했어요."),
    ).toBeDefined();

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
          categoryComparisons: [],
          tagComparisons: [],
          recurringSpendingChanges: [],
        }),
      ),
    );

    render(<SpendingStatisticsPanel refreshKey={0} />);

    expect(
      await screen.findByText("이 기간에는 기록된 지출이 없습니다."),
    ).toBeDefined();
    expect(screen.getByText("변화 없음")).toBeDefined();
    expect(screen.getByText("이전 기간과 달라진 반복 지출이 없습니다.")).toBeDefined();
  });

  test("loads with an older api response that has no recurring changes field", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        jsonResponse({
          ...monthlyStatistics,
          recurringSpendingChanges: undefined,
        }),
      ),
    );

    render(<SpendingStatisticsPanel refreshKey={0} />);

    expect(await screen.findByRole("heading", { name: "반복 지출 변화" })).toBeDefined();
    expect(screen.getByText("이전 기간과 달라진 반복 지출이 없습니다.")).toBeDefined();
  });

  test("shows classification decreases when only the previous period has spending", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        jsonResponse({
          ...monthlyStatistics,
          current: { totalAmount: 0, transactionCount: 0 },
          previous: { totalAmount: 50_000, transactionCount: 1 },
          amountChange: -50_000,
          changeRatePercent: -100,
          byPayer: [],
          byPaymentMethod: [],
          byCategory: [],
          categoryComparisons: [
            {
              key: "HOUSING",
              label: "주거",
              currentAmount: 0,
              currentTransactionCount: 0,
              previousAmount: 50_000,
              previousTransactionCount: 1,
              amountChange: -50_000,
              changeRatePercent: -100,
            },
          ],
          tagComparisons: [],
          recurringSpendingChanges: [],
        }),
      ),
    );

    render(<SpendingStatisticsPanel refreshKey={0} />);

    expect(await screen.findByText("이 기간에는 기록된 지출이 없습니다.")).toBeDefined();
    expect(screen.getByRole("heading", { name: "카테고리 비교" })).toBeDefined();
    expect(screen.getByText("이전 50,000원 · 50,000원 감소 (100%)")).toBeDefined();
    expect(screen.getByText("비교할 태그 지출이 없습니다.")).toBeDefined();
  });

  test("reloads every summary for the selected payer", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(monthlyStatistics))
      .mockResolvedValueOnce(
        jsonResponse({
          ...monthlyStatistics,
          payer: "PARTNER",
          current: { totalAmount: 40_000, transactionCount: 2 },
          previous: { totalAmount: 30_000, transactionCount: 1 },
          amountChange: 10_000,
          changeRatePercent: 33.3,
          byPayer: [
            { key: "2", label: "배우자", amount: 40_000, transactionCount: 2 },
          ],
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<SpendingStatisticsPanel refreshKey={0} />);
    const initialTotalCard = (await screen.findByText("총지출")).parentElement;
    expect(within(initialTotalCard as HTMLElement).getByText("125,000원")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "배우자" }));

    const totalCard = (await screen.findByText("총지출")).parentElement;
    expect(within(totalCard as HTMLElement).getByText("40,000원")).toBeDefined();
    expect(fetchMock.mock.calls[1][0]).toContain("payer=PARTNER");
    expect(screen.queryByRole("heading", { name: "결제자" })).toBeNull();
  });
});

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
