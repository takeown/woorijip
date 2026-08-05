import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { MobileHomeOverview } from "./mobile-home-overview";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("MobileHomeOverview", () => {
  test("shows the monthly summary and active stored-value accounts", async () => {
    const user = userEvent.setup();
    const onOpenEntry = vi.fn();
    vi.stubGlobal(
      "matchMedia",
      vi.fn(() => ({
        addEventListener: vi.fn(),
        matches: true,
        removeEventListener: vi.fn(),
      })),
    );
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        jsonResponse({
          amountChange: -20_000,
          current: { totalAmount: 120_000 },
        }),
      ),
    );

    render(
      <MobileHomeOverview
        accounts={[
          {
            archived: false,
            automationKey: "ONNURI_GIFT_CERTIFICATE",
            balance: 83_000,
            canDelete: false,
            category: "GIFT_CERTIFICATE",
            customCategoryName: null,
            id: 1,
            name: "온누리상품권",
            ownerDisplayName: "나",
            ownerUserId: 1,
          },
        ]}
        onOpenEntry={onOpenEntry}
      />,
    );

    expect(await screen.findByText("120,000원")).toBeDefined();
    expect(screen.getByText("이전 기간보다 20,000원 적어요.")).toBeDefined();
    expect(screen.getByText("83,000원")).toBeDefined();
    expect(screen.getByRole("link", { name: /보유 잔액/ }).getAttribute("href")).toBe(
      "/balances",
    );

    await user.click(screen.getByRole("button", { name: /빠르게 거래 기록하기/ }));
    expect(onOpenEntry).toHaveBeenCalledOnce();
  });
});

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
