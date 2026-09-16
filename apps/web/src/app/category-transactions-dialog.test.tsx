import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { CategoryTransactionsDialog } from "./category-transactions-dialog";

const transaction = {
  id: 1, payerId: 1, merchant: "점심 식당", description: null, amount: 10000,
  category: "FOOD", tags: [], paymentMethod: "CASH", cardIssuer: null,
  storedValueAccountId: null, occurredAt: "2026-08-01T15:30:00Z",
  createdAt: "2026-08-01T15:30:00Z", updatedAt: "2026-08-01T15:30:00Z",
};
const props = {
  category: "FOOD", label: "식비", periodLabel: "2026년 8월",
  from: "2026-08-01", to: "2026-08-31", payer: "PARTNER" as const,
  amount: 20000, count: 2, onClose: vi.fn(), onChanged: vi.fn(),
};
const json = (value: unknown) => new Response(JSON.stringify(value), { status: 200 });
beforeEach(() => {
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", { configurable: true, value: function (this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, "close", { configurable: true, value: function (this: HTMLDialogElement) { this.open = false; } });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.clearAllMocks(); });

test("keeps category date and payer filters across pages and groups by Seoul date", async () => {
  const user = userEvent.setup();
  const fetchMock = vi.fn<typeof fetch>().mockImplementation(async (input) => {
    const url = String(input);
    if (url.includes("/members")) return json([{ userId: 1, displayName: "배우자" }]);
    return json({ items: [{ ...transaction, id: url.includes("cursor=") ? 2 : 1, merchant: url.includes("cursor=") ? "저녁 식당" : "점심 식당" }], nextCursor: url.includes("cursor=") ? null : "next+cursor" });
  });
  vi.stubGlobal("fetch", fetchMock);
  render(<CategoryTransactionsDialog {...props} />);
  expect(await screen.findByText("점심 식당")).toBeDefined();
  expect(screen.getByText("2026년 8월 2일")).toBeDefined();
  await user.click(screen.getByRole("button", { name: "거래 더 보기" }));
  expect(await screen.findByText("저녁 식당")).toBeDefined();
  expect(screen.getByText("점심 식당")).toBeDefined();
  const urls = fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.includes("/transactions?"));
  for (const url of urls) {
    const params = new URL(url).searchParams;
    expect(params.get("category")).toBe("FOOD");
    expect(params.get("payer")).toBe("PARTNER");
    expect(params.get("from")).toBe("2026-08-01");
    expect(params.get("to")).toBe("2026-08-31");
  }
  expect(new URL(urls[1]).searchParams.get("cursor")).toBe("next+cursor");
});

test("retries a failed load and shows the empty state", async () => {
  const user = userEvent.setup();
  let failed = true;
  vi.stubGlobal("fetch", vi.fn(async (input) => {
    if (String(input).includes("/members")) return json([]);
    return failed ? new Response(null, { status: 500 }) : json({ items: [], nextCursor: null });
  }));
  render(<CategoryTransactionsDialog {...props} />);
  expect(await screen.findByRole("alert")).toBeDefined();
  failed = false;
  await user.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(await screen.findByText("이 기간에는 기록된 식비 거래가 없습니다.")).toBeDefined();
});

test("opens the existing editor and refreshes after saving", async () => {
  const user = userEvent.setup();
  vi.stubGlobal("fetch", vi.fn(async (input) => {
    const url = String(input);
    if (url.includes("/members")) return json([{ userId: 1, displayName: "배우자" }]);
    if (url.includes("/stored-value-accounts")) return json([]);
    if (url.includes("/auth/csrf")) return json({ token: "test", headerName: "X-CSRF-TOKEN" });
    if (url.endsWith("/transactions/1")) return json(transaction);
    return json({ items: [transaction], nextCursor: null });
  }));
  render(<CategoryTransactionsDialog {...props} />);
  await user.click(await screen.findByRole("button", { name: "점심 식당 거래 수정" }));
  await user.click(await screen.findByRole("button", { name: "수정 저장" }));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalledOnce());
  expect(await screen.findByRole("button", { name: "점심 식당 거래 수정" })).toBeDefined();
});

test("handles Escape and restores scroll and trigger focus on unmount", async () => {
  vi.stubGlobal("fetch", vi.fn(async (input) => json(String(input).includes("/members") ? [] : { items: [], nextCursor: null })));
  const trigger = document.createElement("button");
  document.body.append(trigger);
  trigger.focus();
  const { unmount } = render(<CategoryTransactionsDialog {...props} />);
  expect(document.body.style.overflow).toBe("hidden");
  fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
  expect(props.onClose).toHaveBeenCalledOnce();
  unmount();
  expect(document.body.style.overflow).toBe("");
  expect(document.activeElement).toBe(trigger);
  trigger.remove();
});
