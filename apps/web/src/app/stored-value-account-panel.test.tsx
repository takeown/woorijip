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
          category: "GIFT_CERTIFICATE",
          automationKey: "ONNURI_GIFT_CERTIFICATE",
          name: "온누리상품권",
          balance: 0,
          archived: false,
          canDelete: true,
        }]}
        householdMembers={[{ userId: 1, displayName: "나" }]}
        onChanged={onChanged}
      />,
    );

    const accountSummary = screen.getByText("온누리상품권").closest("summary");
    expect(accountSummary).not.toBeNull();
    expect(accountSummary?.parentElement?.hasAttribute("open")).toBe(false);

    await user.click(accountSummary!);
    expect(accountSummary?.parentElement?.hasAttribute("open")).toBe(true);
    await user.type(screen.getByLabelText("잔액 추가 금액"), "10000");
    await user.type(screen.getByLabelText("실제 출금액"), "9300");
    await user.type(screen.getByLabelText(/일시/), "2026-08-03T12:00");
    await user.click(screen.getByRole("button", { name: "잔액 추가" }));

    const request = fetchMock.mock.calls[1];
    expect(request[0]).toBe("http://localhost:8080/stored-value-accounts/3/credits");
    expect(JSON.parse(String(request[1]?.body))).toMatchObject({
      balanceAmount: 10_000,
      paidAmount: 9_300,
    });
    expect(onChanged).toHaveBeenCalledOnce();
    expect(accountSummary?.parentElement?.hasAttribute("open")).toBe(false);
  });

  test("creates a custom account for the selected household member", async () => {
    const user = userEvent.setup();
    const onChanged = vi.fn();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(jsonResponse({}, 201));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <StoredValueAccountPanel
        accounts={[]}
        householdMembers={[
          { userId: 1, displayName: "나" },
          { userId: 2, displayName: "배우자" },
        ]}
        onChanged={onChanged}
      />,
    );

    await user.click(screen.getByText("잔액 계정 추가"));
    await user.type(screen.getByLabelText("이름"), "서울사랑상품권");
    await user.selectOptions(screen.getByLabelText("소유자"), "2");
    await user.selectOptions(screen.getByLabelText("종류"), "LOCAL_CURRENCY");
    await user.click(screen.getByRole("button", { name: "계정 추가" }));

    expect(fetchMock.mock.calls[1][0]).toBe("http://localhost:8080/stored-value-accounts");
    expect(JSON.parse(String(fetchMock.mock.calls[1][1]?.body))).toEqual({
      ownerUserId: 2,
      name: "서울사랑상품권",
      category: "LOCAL_CURRENCY",
      automationKey: null,
    });
    expect(onChanged).toHaveBeenCalledOnce();
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}
