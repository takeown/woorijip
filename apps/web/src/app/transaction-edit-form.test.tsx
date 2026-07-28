import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import {
  TransactionEditForm,
  type EditableTransaction,
} from "./transaction-edit-form";

const transaction: EditableTransaction = {
  id: 17,
  payerId: 1,
  merchant: "김밥천국",
  description: "점심",
  amount: 8_000,
  category: "FOOD",
  tags: ["RECURRING_PAYMENT"],
  paymentMethod: "CARD",
  cardIssuer: "SHINHAN",
  occurredAt: "2026-07-21T03:30:00Z",
  createdAt: "2026-07-21T03:30:00Z",
  updatedAt: "2026-07-21T03:30:00Z",
};

const members = [
  { userId: 1, displayName: "나" },
  { userId: 2, displayName: "배우자" },
];

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("TransactionEditForm", () => {
  test("saves edited transaction values", async () => {
    const user = userEvent.setup();
    const onChanged = vi.fn();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionEditForm
        householdMembers={members}
        onCancel={vi.fn()}
        onChanged={onChanged}
        transaction={transaction}
      />,
    );

    const merchant = screen.getByLabelText("가맹점");
    await user.clear(merchant);
    await user.type(merchant, "수정한 가맹점");
    await user.selectOptions(screen.getByLabelText("카테고리"), "LIVING");
    await user.click(screen.getByRole("button", { name: "수정 저장" }));

    const request = fetchMock.mock.calls[1];
    expect(request[0]).toBe("http://localhost:8080/transactions/17");
    expect(request[1]?.method).toBe("PUT");
    expect(JSON.parse(String(request[1]?.body))).toMatchObject({
      expectedUpdatedAt: transaction.updatedAt,
      merchant: "수정한 가맹점",
      category: "LIVING",
      tags: ["RECURRING_PAYMENT"],
    });
    expect(onChanged).toHaveBeenCalledOnce();
  });

  test("deletes after user confirmation", async () => {
    const user = userEvent.setup();
    const onChanged = vi.fn();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionEditForm
        householdMembers={members}
        onCancel={vi.fn()}
        onChanged={onChanged}
        transaction={transaction}
      />,
    );
    await user.click(screen.getByRole("button", { name: "거래 삭제" }));

    expect(window.confirm).toHaveBeenCalled();
    const request = fetchMock.mock.calls[1];
    expect(request[1]?.method).toBe("DELETE");
    expect(JSON.parse(String(request[1]?.body))).toEqual({
      expectedUpdatedAt: transaction.updatedAt,
    });
    expect(onChanged).toHaveBeenCalledOnce();
  });
});

function csrfResponse(): Response {
  return jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" });
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
