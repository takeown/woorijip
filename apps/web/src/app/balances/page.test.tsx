import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { BalanceManagementPage } from "./page";

vi.mock("../stored-value-account-panel", () => ({
  StoredValueAccountPanel: ({
    accounts,
    householdMembers,
    onChanged,
  }: {
    accounts: { name: string }[];
    householdMembers: { displayName: string }[];
    onChanged: () => Promise<void>;
  }) => (
    <div>
      <p>{accounts.map((account) => account.name).join(", ")}</p>
      <p>{householdMembers.map((member) => member.displayName).join(", ")}</p>
      <button onClick={onChanged} type="button">잔액 새로고침</button>
    </div>
  ),
}));

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("BalanceManagementPage", () => {
  test("loads members and accounts and refreshes accounts after a change", async () => {
    const user = userEvent.setup();
    let accountRequestCount = 0;
    const fetchMock = vi.fn<typeof fetch>(async (input) => {
      const url = String(input);
      if (url.endsWith("/households/current/members")) {
        return jsonResponse([{ userId: 1, displayName: "나" }]);
      }
      accountRequestCount += 1;
      return jsonResponse([
        storedValueAccount(accountRequestCount === 1 ? "온누리상품권" : "지역화폐"),
      ]);
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<BalanceManagementPage />);

    expect(await screen.findByText("온누리상품권")).toBeDefined();
    expect(screen.getByText("나")).toBeDefined();
    expect(screen.getByRole("link", { name: "← 거래로 돌아가기" }).getAttribute("href")).toBe(
      "/",
    );

    await user.click(screen.getByRole("button", { name: "잔액 새로고침" }));

    expect(await screen.findByText("지역화폐")).toBeDefined();
    expect(accountRequestCount).toBe(2);
  });
});

function storedValueAccount(name: string) {
  return {
    archived: false,
    automationKey: null,
    balance: 10_000,
    canDelete: true,
    category: "LOCAL_CURRENCY",
    customCategoryName: null,
    id: 1,
    name,
    ownerDisplayName: "나",
    ownerUserId: 1,
  };
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
