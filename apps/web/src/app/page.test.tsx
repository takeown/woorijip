import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { TransactionsPage } from "./page";

vi.mock("./ai-transaction-draft-form", () => ({
  AiTransactionDraftForm: () => <div>AI 입력</div>,
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
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async (input) => {
        const url = String(input);
        if (url.endsWith("/households/current/members")) {
          return jsonResponse([{ userId: 1, displayName: "나" }]);
        }
        if (url.endsWith("/stored-value-accounts")) return jsonResponse([]);
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
    expect(entryPanel).not.toBeNull();
    if (entryPanel) entryPanel.scrollTop = 240;

    await user.click(screen.getByRole("button", { name: "거래 추가" }));

    expect(screen.getByRole("dialog", { name: "거래 입력" })).toBeDefined();
    await waitFor(() => expect(entryPanel?.scrollTop).toBe(0));
    await user.click(screen.getByRole("button", { name: "거래 추가 닫기" }));
    expect(screen.queryByRole("dialog", { name: "거래 입력" })).toBeNull();
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
