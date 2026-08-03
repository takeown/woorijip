import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { AiTransactionDraftForm } from "./ai-transaction-draft-form";

const readyDraft = {
  status: "READY",
  merchant: "김밥천국",
  description: "점심 식사",
  amount: 8_000,
  category: "FOOD",
  tags: [],
  occurredAt: "2026-07-21T12:30:00+09:00",
  payerId: 1,
  payerDisplayName: "나",
  paymentMethod: "CARD",
  cardIssuer: "SHINHAN",
  storedValueAccountType: null,
  message: "아래 거래 내용을 확인해 주세요.",
};

const members = [
  { userId: 1, displayName: "나" },
  { userId: 2, displayName: "배우자" },
];

const storedValueAccounts = [
  {
    id: 11,
    ownerUserId: 1,
    ownerDisplayName: "나",
    type: "ONNURI_GIFT_CERTIFICATE" as const,
    name: "온누리상품권",
    balance: 100_000,
  },
  {
    id: 12,
    ownerUserId: 1,
    ownerDisplayName: "나",
    type: "PREGNANCY_VOUCHER" as const,
    name: "임산부 바우처",
    balance: 500_000,
  },
  {
    id: 13,
    ownerUserId: 2,
    ownerDisplayName: "배우자",
    type: "ONNURI_GIFT_CERTIFICATE" as const,
    name: "온누리상품권",
    balance: 50_000,
  },
];

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("AiTransactionDraftForm", () => {
  test("saves the values edited by the user instead of the generated draft", async () => {
    const user = userEvent.setup();
    const onCreated = vi.fn();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(jsonResponse(readyDraft))
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(new Response(null, { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);

    render(<AiTransactionDraftForm householdMembers={members} onCreated={onCreated} />);

    await user.type(screen.getByLabelText("자연어로 입력"), "김밥천국에서 8천원 썼어");
    await user.click(screen.getByRole("button", { name: "AI로 거래 초안 만들기" }));

    const merchant = await screen.findByLabelText("가맹점");
    await user.clear(merchant);
    await user.type(merchant, "새 가맹점");
    await user.clear(screen.getByLabelText(/내역/));
    await user.type(screen.getByLabelText(/내역/), "수정한 내역");
    await user.clear(screen.getByLabelText("금액"));
    await user.type(screen.getByLabelText("금액"), "12000");
    await user.selectOptions(screen.getByLabelText("결제자"), "2");
    await user.selectOptions(screen.getByLabelText("결제 경로"), "CASH");
    await user.click(screen.getByRole("button", { name: "확인하고 저장" }));

    expect(fetchMock).toHaveBeenCalledTimes(4);
    const saveRequest = fetchMock.mock.calls[3];
    const requestBody = JSON.parse(String(saveRequest[1]?.body));
    expect(requestBody).toMatchObject({
      payerId: 2,
      merchant: "새 가맹점",
      description: "수정한 내역",
      amount: 12_000,
      category: "FOOD",
      tags: [],
      classificationSource: "AI",
      paymentMethod: "CASH",
      cardIssuer: null,
    });
    expect(onCreated).toHaveBeenCalledOnce();
    expect(screen.queryByLabelText("가맹점")).toBeNull();
  });

  test("selects the Onnuri balance for a generated QR draft", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(jsonResponse({
        ...readyDraft,
        merchant: "GS25",
        description: "기저귀",
        amount: 24_000,
        category: "CHILDCARE",
        paymentMethod: "QR",
        cardIssuer: null,
        storedValueAccountType: "ONNURI_GIFT_CERTIFICATE",
      }))
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(new Response(null, { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AiTransactionDraftForm
        householdMembers={members}
        onCreated={vi.fn()}
        storedValueAccounts={storedValueAccounts}
      />,
    );

    await user.type(screen.getByLabelText("자연어로 입력"), "GS25에서 온누리상품권 QR로 기저귀 24000원 결제했어");
    await user.click(screen.getByRole("button", { name: "AI로 거래 초안 만들기" }));

    expect((await screen.findByLabelText("결제 경로") as HTMLSelectElement).value).toBe("QR");
    expect((screen.getByLabelText(/사용 잔액/) as HTMLSelectElement).value).toBe("11");
    await user.selectOptions(screen.getByLabelText("결제자"), "2");
    expect((screen.getByLabelText(/사용 잔액/) as HTMLSelectElement).value).toBe("13");
    await user.click(screen.getByRole("button", { name: "확인하고 저장" }));

    const requestBody = JSON.parse(String(fetchMock.mock.calls[3][1]?.body));
    expect(requestBody).toMatchObject({
      paymentMethod: "QR",
      cardIssuer: null,
      payerId: 2,
      storedValueAccountId: 13,
    });
  });

  test("cancels a generated draft without saving it", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(jsonResponse(readyDraft));
    vi.stubGlobal("fetch", fetchMock);

    render(<AiTransactionDraftForm householdMembers={members} onCreated={vi.fn()} />);

    const naturalLanguageInput = screen.getByLabelText("자연어로 입력") as HTMLTextAreaElement;
    await user.type(naturalLanguageInput, "김밥천국에서 8천원 썼어");
    await user.click(screen.getByRole("button", { name: "AI로 거래 초안 만들기" }));
    await screen.findByLabelText("가맹점");
    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(screen.queryByLabelText("가맹점")).toBeNull();
    expect(naturalLanguageInput.value).toBe("");
  });

  test("shows the sensitive input message returned by the API", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: "SENSITIVE_AI_INPUT",
            detail: "카드번호를 제거한 뒤 다시 입력해 주세요.",
          },
          400,
        ),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<AiTransactionDraftForm householdMembers={members} onCreated={vi.fn()} />);

    await user.type(screen.getByLabelText("자연어로 입력"), "카드번호 포함 거래");
    await user.click(screen.getByRole("button", { name: "AI로 거래 초안 만들기" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "카드번호를 제거한 뒤 다시 입력해 주세요.",
    );
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}
