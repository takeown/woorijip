import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { TransactionsPage } from "./page";

vi.mock("./ai-transaction-draft-form", () => ({
  AiTransactionDraftForm: ({ onCreated }: { onCreated: () => Promise<void> }) => (
    <button onClick={() => void onCreated()} type="button">AI 테스트 거래 생성</button>
  ),
}));

vi.mock("./transaction-form", () => ({
  TransactionForm: () => <div>직접 입력 폼</div>,
}));

vi.mock("./transaction-edit-form", () => ({
  TransactionEditForm: () => <div>수정 폼</div>,
}));

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("TransactionsPage", () => {
  test("opens and closes the mobile transaction entry panel", async () => {
    const user = userEvent.setup();
    mockMobileMediaQuery();
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async (input) => {
        const url = String(input);
        if (url.endsWith("/households/current/members")) {
          return jsonResponse([{ userId: 1, displayName: "나" }]);
        }
        if (url.endsWith("/stored-value-accounts")) return jsonResponse([]);
        if (url.includes("/statistics/spending")) {
          return jsonResponse({ amountChange: 0, current: { totalAmount: 0 } });
        }
        return jsonResponse({ items: [], nextCursor: null });
      }),
    );

    const { container } = render(
      <TransactionsPage
        currentUser={{ id: 1, displayName: "나", householdId: 10 }}
      />,
    );

    expect(await screen.findByRole("button", { name: "거래 추가" })).toBeDefined();
    expect(screen.getByRole("link", { name: /보유 잔액/ }).getAttribute("href")).toBe(
      "/balances",
    );
    const entryPanel = container.querySelector<HTMLElement>(
      "[aria-labelledby='transaction-entry-title']",
    );
    const transactionSection = screen.getByRole("heading", { name: "거래 내역" }).closest("section");
    const initialInert = transactionSection?.inert;
    expect(entryPanel).not.toBeNull();
    if (entryPanel) entryPanel.scrollTop = 240;

    const entryTrigger = screen.getByRole("button", { name: "거래 추가" });
    await user.click(entryTrigger);

    expect(screen.getByRole("dialog", { name: "거래 입력" })).toBeDefined();
    expect(entryPanel?.className).toContain("overflow-x-hidden");
    await waitFor(() => expect(entryPanel?.scrollTop).toBe(0));
    expect(transactionSection?.inert).toBe(true);
    await user.click(screen.getByRole("button", { name: "거래 추가 닫기" }));
    expect(screen.queryByRole("dialog", { name: "거래 입력" })).toBeNull();
    expect(transactionSection?.inert).toBe(initialInert);
    expect(document.activeElement).toBe(entryTrigger);
  });

  test("closes the mobile entry panel when the viewport changes to desktop", async () => {
    const user = userEvent.setup();
    const mediaQuery = mockMobileMediaQuery();
    vi.stubGlobal("fetch", createInitialFetchMock());

    render(
      <TransactionsPage
        currentUser={{ id: 1, displayName: "나", householdId: 10 }}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "거래 추가" }));
    expect(document.body.style.overflow).toBe("hidden");

    act(() => mediaQuery.switchToDesktop());

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: "거래 입력" })).toBeNull();
      expect(document.body.style.overflow).toBe("");
    });
  });

  test("refreshes the monthly summary after creating a transaction", async () => {
    const user = userEvent.setup();
    mockMobileMediaQuery();
    let summaryRequestCount = 0;
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const url = String(input);
      if (url.includes("/statistics/spending")) {
        summaryRequestCount += 1;
        return jsonResponse({
          amountChange: 0,
          current: { totalAmount: summaryRequestCount === 1 ? 100_000 : 108_000 },
        });
      }
      if (url.endsWith("/households/current/members")) {
        return jsonResponse([{ userId: 1, displayName: "나" }]);
      }
      if (url.endsWith("/stored-value-accounts")) return jsonResponse([]);
      return jsonResponse({ items: [], nextCursor: null });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionsPage
        currentUser={{ id: 1, displayName: "나", householdId: 10 }}
      />,
    );

    expect(await screen.findByText("100,000원")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "거래 추가" }));
    await user.click(screen.getByRole("button", { name: "AI 테스트 거래 생성" }));

    expect(await screen.findByText("108,000원")).toBeDefined();
    expect(summaryRequestCount).toBe(2);
  });

  test("loads the next cursor page and resets pagination when the payer filter changes", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const url = String(input);
      if (url.endsWith("/households/current/members")) {
        return jsonResponse([
          { userId: 1, displayName: "나" },
          { userId: 2, displayName: "배우자" },
        ]);
      }
      if (url.endsWith("/stored-value-accounts")) return jsonResponse([]);
      if (url.includes("cursor=next-page")) {
        return jsonResponse({
          items: [transaction(2, "이전 거래")],
          nextCursor: null,
        });
      }
      if (url.includes("payer=me")) {
        return jsonResponse({
          items: [transaction(3, "내 거래")],
          nextCursor: null,
        });
      }
      return jsonResponse({
        items: [transaction(1, "최근 거래")],
        nextCursor: "next-page",
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionsPage
        currentUser={{ id: 1, displayName: "나", householdId: 10 }}
      />,
    );

    expect(await screen.findByText("최근 거래")).toBeDefined();
    expect(screen.getByText("1건 표시")).toBeDefined();

    await user.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByText("이전 거래")).toBeDefined();
    expect(screen.getByText("2건 표시")).toBeDefined();
    expect(
      fetchMock.mock.calls.some(([input]) =>
        String(input).includes("payer=all&cursor=next-page"),
      ),
    ).toBe(true);

    await user.click(screen.getByRole("button", { name: "내 결제" }));

    expect(await screen.findByText("내 거래")).toBeDefined();
    expect(screen.queryByText("최근 거래")).toBeNull();
    expect(screen.queryByText("이전 거래")).toBeNull();
    expect(
      fetchMock.mock.calls.some(
        ([input]) =>
          String(input).endsWith("/transactions?payer=me"),
      ),
    ).toBe(true);
  });

  test("searches by text and date range while preserving filters across cursor pages", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const url = String(input);
      if (url.endsWith("/households/current/members")) {
        return jsonResponse([
          { userId: 1, displayName: "나" },
          { userId: 2, displayName: "배우자" },
        ]);
      }
      if (url.endsWith("/stored-value-accounts")) return jsonResponse([]);
      if (url.includes("cursor=filtered-next")) {
        return jsonResponse({ items: [transaction(3, "쿠팡 이전 거래")], nextCursor: null });
      }
      if (url.includes("q=%EC%BF%A0%ED%8C%A1")) {
        return jsonResponse({ items: [transaction(2, "쿠팡 최근 거래")], nextCursor: "filtered-next" });
      }
      return jsonResponse({ items: [transaction(1, "최근 거래")], nextCursor: null });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionsPage
        currentUser={{ id: 1, displayName: "나", householdId: 10 }}
      />,
    );

    expect(await screen.findByText("최근 거래")).toBeDefined();
    await user.type(screen.getByLabelText("가맹점·내역 검색"), "쿠팡");
    await user.click(screen.getByRole("button", { name: "검색" }));
    expect(await screen.findByText("쿠팡 최근 거래")).toBeDefined();

    await user.click(screen.getByText("기간"));
    await user.type(screen.getByLabelText("시작 날짜"), "2026-08-01");
    await user.type(screen.getByLabelText("종료 날짜"), "2026-08-31");
    await user.click(screen.getByRole("button", { name: "기간 적용" }));

    await waitFor(() => {
      const request = latestTransactionRequest(fetchMock);
      expect(request.searchParams.get("q")).toBe("쿠팡");
      expect(request.searchParams.get("from")).toBe("2026-08-01");
      expect(request.searchParams.get("to")).toBe("2026-08-31");
      expect(request.searchParams.has("cursor")).toBe(false);
    });

    await user.click(screen.getByRole("button", { name: "더 보기" }));
    expect(await screen.findByText("쿠팡 이전 거래")).toBeDefined();
    const nextPageRequest = latestTransactionRequest(fetchMock);
    expect(nextPageRequest.searchParams.get("q")).toBe("쿠팡");
    expect(nextPageRequest.searchParams.get("from")).toBe("2026-08-01");
    expect(nextPageRequest.searchParams.get("to")).toBe("2026-08-31");
    expect(nextPageRequest.searchParams.get("cursor")).toBe("filtered-next");
  });

  test("validates the date range before requesting filtered transactions", async () => {
    const user = userEvent.setup();
    const fetchMock = createInitialFetchMock();
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionsPage
        currentUser={{ id: 1, displayName: "나", householdId: 10 }}
      />,
    );

    await screen.findByText("아직 기록한 거래가 없습니다.");
    const initialRequestCount = fetchMock.mock.calls.length;
    await user.click(screen.getByText("기간"));
    await user.type(screen.getByLabelText("시작 날짜"), "2026-08-02");
    await user.type(screen.getByLabelText("종료 날짜"), "2026-08-01");
    await user.click(screen.getByRole("button", { name: "기간 적용" }));

    expect((await screen.findByRole("alert")).textContent).toBe(
      "시작 날짜는 종료 날짜보다 늦을 수 없습니다.",
    );
    expect(fetchMock.mock.calls).toHaveLength(initialRequestCount);
  });

  test("keeps loaded transactions visible when loading the next page fails", async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const url = String(input);
      if (url.endsWith("/households/current/members")) {
        return jsonResponse([{ userId: 1, displayName: "나" }]);
      }
      if (url.endsWith("/stored-value-accounts")) return jsonResponse([]);
      if (url.includes("cursor=next-page")) {
        return new Response(null, { status: 500 });
      }
      return jsonResponse({
        items: [transaction(1, "최근 거래")],
        nextCursor: "next-page",
      });
    });
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionsPage
        currentUser={{ id: 1, displayName: "나", householdId: 10 }}
      />,
    );

    expect(await screen.findByText("최근 거래")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "더 보기" }));

    expect(
      await screen.findByText("거래 내역을 더 불러오지 못했습니다."),
    ).toBeDefined();
    expect(screen.getByText("최근 거래")).toBeDefined();
    expect(screen.getByRole("button", { name: "더 보기" })).toBeDefined();
  });
});

function transaction(id: number, merchant: string) {
  return {
    id,
    payerId: 1,
    merchant,
    description: null,
    amount: 8_000,
    category: "FOOD",
    tags: [],
    paymentMethod: "CARD",
    cardIssuer: "SHINHAN",
    storedValueAccountId: null,
    occurredAt: "2026-07-30T12:00:00+09:00",
    createdAt: "2026-07-30T12:00:00+09:00",
    updatedAt: "2026-07-30T12:00:00+09:00",
  };
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}

function latestTransactionRequest(fetchMock: ReturnType<typeof vi.fn<typeof fetch>>): URL {
  const request = [...fetchMock.mock.calls]
    .reverse()
    .map(([input]) => String(input))
    .find((url) => url.includes("/transactions?"));
  if (!request) throw new Error("거래 조회 요청이 없습니다.");
  return new URL(request);
}

function createInitialFetchMock() {
  return vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url.endsWith("/households/current/members")) {
      return jsonResponse([{ userId: 1, displayName: "나" }]);
    }
    if (url.endsWith("/stored-value-accounts")) return jsonResponse([]);
    if (url.includes("/statistics/spending")) {
      return jsonResponse({ amountChange: 0, current: { totalAmount: 0 } });
    }
    return jsonResponse({ items: [], nextCursor: null });
  });
}

function mockMobileMediaQuery() {
  let matches = true;
  const listeners = new Set<(event: MediaQueryListEvent) => void>();
  const mediaQuery = {
    addEventListener: vi.fn((type: string, listener: (event: MediaQueryListEvent) => void) => {
      if (type === "change") listeners.add(listener);
    }),
    get matches() {
      return matches;
    },
    removeEventListener: vi.fn((type: string, listener: (event: MediaQueryListEvent) => void) => {
      if (type === "change") listeners.delete(listener);
    }),
  };
  vi.stubGlobal("matchMedia", vi.fn(() => mediaQuery));

  return {
    switchToDesktop() {
      matches = false;
      listeners.forEach((listener) => listener({ matches } as MediaQueryListEvent));
    },
  };
}
