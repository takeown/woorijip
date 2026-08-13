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
  monthlySummary: {
    topCategory: { key: "FOOD", label: "식비", amount: 125_000, transactionCount: 4 },
    sharePercent: 100,
    categoryAmountChange: 25_000,
    categoryChangeRatePercent: 25,
    evidenceTransactions: [
      {
        id: 10,
        merchant: "우리동네 마트",
        amount: 75_000,
        occurredAt: "2026-07-20T19:30:00+09:00",
        payerLabel: "나",
      },
      {
        id: 11,
        merchant: "주말 장보기",
        amount: 50_000,
        occurredAt: "2026-07-12T11:00:00+09:00",
        payerLabel: "배우자",
      },
    ],
  },
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
    expect(screen.getByRole("heading", { name: "2026년 7월 돈 어디 갔어?" })).toBeDefined();
    expect(screen.getByText("식비에 가장 많이 썼어요.")).toBeDefined();
    expect(screen.getByText("우리동네 마트")).toBeDefined();
    expect(screen.getByText("75,000원")).toBeDefined();
    expect(screen.getByText("7월 20일 · 나")).toBeDefined();

    await user.click(screen.getByRole("button", { name: "일간" }));

    const reloadedTotalCard = (await screen.findByText("총지출")).parentElement;
    expect(within(reloadedTotalCard as HTMLElement).getByText("8,000원")).toBeDefined();
    expect(fetchMock.mock.calls[1][0]).toContain("period=DAY");
    expect(fetchMock.mock.calls[1][0]).toContain("includeMonthlySummary=true");
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
          monthlySummary: null,
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

  test("asks a free-form question and shows evidence transactions", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(monthlyStatistics))
      .mockResolvedValueOnce(
        jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          status: "ANSWERED",
          answer: "식비는 동네 마트 지출의 영향이 가장 컸어요.",
          evidenceTransactions: [
            {
              id: 21,
              merchant: "동네 마트",
              amount: 48_000,
              occurredAt: "2026-08-10T18:00:00+09:00",
              payerLabel: "나",
            },
          ],
          dataLimited: true,
          remainingRequestsToday: 19,
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<SpendingStatisticsPanel refreshKey={0} />);
    await screen.findByText("총지출");
    await user.type(
      screen.getByRole("textbox", { name: "가계 지출 질문" }),
      "식비가 왜 늘었어?",
    );
    await user.click(screen.getByRole("button", { name: "물어보기" }));

    expect(await screen.findByText("식비는 동네 마트 지출의 영향이 가장 컸어요.")).toBeDefined();
    expect(screen.getByRole("heading", { name: "이 답변의 근거" })).toBeDefined();
    expect(screen.getByText("동네 마트")).toBeDefined();
    expect(screen.getByText("48,000원")).toBeDefined();
    expect(screen.getByText("오늘 19번 더 물어볼 수 있어요.")).toBeDefined();
    expect(
      screen.getByText("최근 거래 일부와 외부 전송에 안전한 거래만 기준으로 살펴봤어요."),
    ).toBeDefined();
    expect(fetchMock.mock.calls[2][0]).toContain("/statistics/spending-answers");
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method: "POST",
      body: JSON.stringify({ question: "식비가 왜 늘었어?" }),
    });
  });

  test("shows the api error when the daily question limit is exhausted", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(jsonResponse(monthlyStatistics))
        .mockResolvedValueOnce(
          jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }),
        )
        .mockResolvedValueOnce(
          jsonResponse(
            { detail: "오늘 사용할 수 있는 가계 분석 횟수를 모두 사용했습니다." },
            429,
          ),
        ),
    );

    render(<SpendingStatisticsPanel refreshKey={0} />);
    await screen.findByText("총지출");
    await user.type(screen.getByRole("textbox", { name: "가계 지출 질문" }), "또 알려줘");
    await user.click(screen.getByRole("button", { name: "물어보기" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "오늘 사용할 수 있는 가계 분석 횟수를 모두 사용했습니다.",
    );
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}
