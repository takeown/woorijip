import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
            payerId: 2,
            merchant: "우리동네 마트",
            description: "저녁 장보기",
            amount: 45_000,
            category: "LIVING",
            tags: ["RECURRING_PAYMENT"],
            paymentMethod: "CARD",
            cardIssuer: "SHINHAN",
            storedValueAccountId: null,
            occurredAt: "2026-07-20T13:40:00+09:00",
            createdAt: "2026-07-20T13:40:00+09:00",
            updatedAt: "2026-07-20T13:40:00+09:00",
            payerLabel: "배우자",
            paymentMethodLabel: "카드",
            categoryLabel: "생활",
            tagLabels: ["정기결제"],
          },
          {
            id: 21,
            payerId: 1,
            merchant: "아이사랑 약국",
            description: null,
            amount: 18_000,
            category: "CHILDCARE",
            tags: [],
            paymentMethod: "CASH",
            cardIssuer: null,
            storedValueAccountId: null,
            occurredAt: "2026-07-20T18:20:00+09:00",
            createdAt: "2026-07-20T18:20:00+09:00",
            updatedAt: "2026-07-20T18:20:00+09:00",
            payerLabel: "나",
            paymentMethodLabel: "현금",
            categoryLabel: "육아",
            tagLabels: [],
          },
        ],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(
      <DailySpendingDetailsPanel
        date="2026-07-20"
        payer="PARTNER"
        statsDate="2026-07-26"
      />,
    );

    expect(await screen.findByRole("heading", { name: "2026년 7월 20일 월요일 지출" })).toBeDefined();
    expect(screen.getByText("배우자 결제 내역입니다.")).toBeDefined();
    expect(screen.getByText("2건 모두 표시")).toBeDefined();
    const table = screen.getByRole("table", { name: "선택한 날짜의 전체 거래 내역" });
    expect(within(table).getByText("우리동네 마트")).toBeDefined();
    expect(within(table).getByText("저녁 장보기")).toBeDefined();
    expect(within(table).getByText("45,000원")).toBeDefined();
    expect(within(table).getByText("정기결제")).toBeDefined();
    expect(screen.getByRole("link", { name: "이전 날짜" }).getAttribute("href")).toBe(
      "/stats/daily/2026-07-19?payer=PARTNER&statsDate=2026-07-26",
    );
    expect(screen.getByRole("link", { name: "다음 날짜" }).getAttribute("href")).toBe(
      "/stats/daily/2026-07-21?payer=PARTNER&statsDate=2026-07-26",
    );
    expect(
      screen.getByRole("link", { name: "← 소비 캘린더로 돌아가기" }).getAttribute("href"),
    ).toBe(
      "/stats?period=MONTH&payer=PARTNER&date=2026-07-26&calendar=open",
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

    render(
      <DailySpendingDetailsPanel date="2026-07-20" payer="ALL" statsDate="2026-07-20" />,
    );

    expect(await screen.findByText("이날에는 기록된 거래가 없습니다.")).toBeDefined();
    expect(screen.getByText("0건 모두 표시")).toBeDefined();
  });

  test("opens and cancels the existing transaction edit form", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({
        current: {
          totalAmount: 45_000,
          coupleLivingAmount: 45_000,
          childcareAmount: 0,
          transactionCount: 1,
        },
        dailyTransactions: [{
          id: 20,
          payerId: 2,
          merchant: "우리동네 마트",
          description: "저녁 장보기",
          amount: 45_000,
          category: "LIVING",
          tags: ["RECURRING_PAYMENT"],
          paymentMethod: "CARD",
          cardIssuer: "SHINHAN",
          storedValueAccountId: null,
          occurredAt: "2026-07-20T13:40:00+09:00",
          createdAt: "2026-07-20T13:40:00+09:00",
          updatedAt: "2026-07-20T13:40:00+09:00",
          payerLabel: "배우자",
          paymentMethodLabel: "카드",
          categoryLabel: "생활",
          tagLabels: ["정기결제"],
        }],
      }))
      .mockResolvedValueOnce(jsonResponse([
        { userId: 1, displayName: "나" },
        { userId: 2, displayName: "배우자" },
      ]))
      .mockResolvedValueOnce(jsonResponse([]));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <DailySpendingDetailsPanel date="2026-07-20" payer="ALL" statsDate="2026-07-20" />,
    );

    await screen.findByText("1건 모두 표시");
    await user.click(screen.getByRole("button", { name: "거래 수정" }));

    expect(await screen.findByDisplayValue("우리동네 마트")).toBeDefined();
    expect(fetchMock.mock.calls[1][0]).toBe(
      "http://localhost:8080/households/current/members",
    );
    expect(fetchMock.mock.calls[2][0]).toBe(
      "http://localhost:8080/stored-value-accounts",
    );

    await user.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("button", { name: "수정 저장" })).toBeNull();
    expect(screen.getByRole("button", { name: "거래 수정" })).toBeDefined();
  });

  test("refreshes the same daily details after editing a transaction", async () => {
    const user = userEvent.setup();
    const transaction = {
      id: 20,
      payerId: 2,
      merchant: "우리동네 마트",
      description: "저녁 장보기",
      amount: 45_000,
      category: "LIVING",
      tags: ["RECURRING_PAYMENT"],
      paymentMethod: "CARD",
      cardIssuer: "SHINHAN",
      storedValueAccountId: null,
      occurredAt: "2026-07-20T13:40:00+09:00",
      createdAt: "2026-07-20T13:40:00+09:00",
      updatedAt: "2026-07-20T13:40:00+09:00",
      payerLabel: "배우자",
      paymentMethodLabel: "카드",
      categoryLabel: "생활",
      tagLabels: ["정기결제"],
    };
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({
        current: {
          totalAmount: 45_000,
          coupleLivingAmount: 45_000,
          childcareAmount: 0,
          transactionCount: 1,
        },
        dailyTransactions: [transaction],
      }))
      .mockResolvedValueOnce(jsonResponse([
        { userId: 1, displayName: "나" },
        { userId: 2, displayName: "배우자" },
      ]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({
        token: "csrf-token",
        headerName: "X-XSRF-TOKEN",
      }))
      .mockResolvedValueOnce(jsonResponse({}))
      .mockResolvedValueOnce(jsonResponse({
        current: {
          totalAmount: 46_000,
          coupleLivingAmount: 46_000,
          childcareAmount: 0,
          transactionCount: 1,
        },
        dailyTransactions: [{
          ...transaction,
          amount: 46_000,
          updatedAt: "2026-07-20T14:00:00+09:00",
        }],
      }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <DailySpendingDetailsPanel date="2026-07-20" payer="ALL" statsDate="2026-07-20" />,
    );

    await screen.findByText("1건 모두 표시");
    await user.click(screen.getByRole("button", { name: "거래 수정" }));
    const amount = await screen.findByLabelText("금액");
    await user.clear(amount);
    await user.type(amount, "46000");
    await user.click(screen.getByRole("button", { name: "수정 저장" }));

    expect((await screen.findAllByText("46,000원")).length).toBeGreaterThanOrEqual(3);
    expect(fetchMock.mock.calls[5][0]).toContain(
      "/statistics/spending?period=DAY&payer=ALL&date=2026-07-20",
    );
  });

  test("does not describe a missing daily transaction payload as an empty day", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        jsonResponse({
          current: {
            totalAmount: 45_000,
            coupleLivingAmount: 45_000,
            childcareAmount: 0,
            transactionCount: 1,
          },
        }),
      ),
    );

    render(
      <DailySpendingDetailsPanel date="2026-07-20" payer="ALL" statsDate="2026-07-20" />,
    );

    expect((await screen.findByRole("alert")).textContent).toContain(
      "이날 거래 상세를 불러오지 못했습니다.",
    );
    expect(screen.queryByText("이날에는 기록된 거래가 없습니다.")).toBeNull();
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}
