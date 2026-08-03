import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { StoredValueAccountPanel } from "./stored-value-account-panel";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("StoredValueAccountPanel", () => {
  test("records face value and actual bank withdrawal separately", async () => {
    const user = userEvent.setup();
    const onChanged = vi.fn();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <StoredValueAccountPanel
        accounts={[{
          id: 3,
          ownerUserId: 1,
          ownerDisplayName: "나",
          type: "ONNURI_GIFT_CERTIFICATE",
          name: "온누리상품권",
          balance: 0,
        }]}
        onChanged={onChanged}
      />,
    );

    const accountSummary = screen.getByText("온누리상품권").closest("summary");
    expect(accountSummary).not.toBeNull();
    expect(accountSummary?.parentElement?.hasAttribute("open")).toBe(false);

    await user.click(accountSummary!);
    expect(accountSummary?.parentElement?.hasAttribute("open")).toBe(true);
    await user.type(screen.getByLabelText("충전 금액"), "10000");
    await user.type(screen.getByLabelText("계좌 출금액"), "9300");
    await user.type(screen.getByLabelText(/일시/), "2026-08-03T12:00");
    await user.click(screen.getByRole("button", { name: "충전 기록" }));

    const request = fetchMock.mock.calls[1];
    expect(request[0]).toBe("http://localhost:8080/stored-value-accounts/3/credits");
    expect(JSON.parse(String(request[1]?.body))).toMatchObject({
      balanceAmount: 10_000,
      paidAmount: 9_300,
    });
    expect(onChanged).toHaveBeenCalledOnce();
    expect(accountSummary?.parentElement?.hasAttribute("open")).toBe(false);
  });
});

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
