import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { DailySpendingDetailsPanel } from "./daily-spending-details-panel";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("DailySpendingDetailsPanel", () => {
  test("shows every transaction for the routed date and payer", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(
      jsonResponse({
        current: {
          totalAmount: 63_000,
          coupleLivingAmount: 45_000,
          childcareAmount: 18_000,
          transactionCount: 2,
        },
        dailyTransactions: [
          {
            id: 20,
            merchant: "우리동네 마트",
            description: "저녁 장보기",
            amount: 45_000,
            occurredAt: "2026-07-20T13:40:00+09:00",
            payerLabel: "배우자",
            paymentMethodLabel: "카드",
            categoryLabel: "생활",
            tagLabels: ["정기결제"],
          },
          {
            id: 21,
            merchant: "아이사랑 약국",
            description: null,
            amount: 18_000,
            occurredAt: "2026-07-20T18:20:00+09:00",
            payerLabel: "나",
            paymentMethodLabel: "현금",
            categoryLabel: "육아",
            tagLabels: [],
          },
        ],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<DailySpendingDetailsPanel date="2026-07-20" payer="PARTNER" />);

    expect(await screen.findByRole("heading", { name: "2026년 7월 20일 월요일 지출" })).toBeDefined();
    expect(screen.getByText("배우자 결제 내역입니다.")).toBeDefined();
    expect(screen.getByText("2건 모두 표시")).toBeDefined();
    const table = screen.getByRole("table", { name: "선택한 날짜의 전체 거래 내역" });
    expect(within(table).getByText("우리동네 마트")).toBeDefined();
    expect(within(table).getByText("저녁 장보기")).toBeDefined();
    expect(within(table).getByText("45,000원")).toBeDefined();
    expect(within(table).getByText("정기결제")).toBeDefined();
    expect(screen.getByRole("link", { name: "이전 날짜" }).getAttribute("href")).toBe(
      "/stats/daily/2026-07-19?payer=PARTNER",
    );
    expect(screen.getByRole("link", { name: "다음 날짜" }).getAttribute("href")).toBe(
      "/stats/daily/2026-07-21?payer=PARTNER",
    );
    expect(fetchMock.mock.calls[0][0]).toContain("period=DAY");
    expect(fetchMock.mock.calls[0][0]).toContain("payer=PARTNER");
    expect(fetchMock.mock.calls[0][0]).toContain("date=2026-07-20");
    expect(fetchMock.mock.calls[0][0]).toContain("includeDailyTransactions=true");
  });

  test("shows an empty transaction state", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        jsonResponse({
          current: {
            totalAmount: 0,
            coupleLivingAmount: 0,
            childcareAmount: 0,
            transactionCount: 0,
          },
          dailyTransactions: [],
        }),
      ),
    );

    render(<DailySpendingDetailsPanel date="2026-07-20" payer="ALL" />);

    expect(await screen.findByText("이날에는 기록된 거래가 없습니다.")).toBeDefined();
    expect(screen.getByText("0건 모두 표시")).toBeDefined();
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}
