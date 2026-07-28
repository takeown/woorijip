import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { TransactionForm } from "./transaction-form";

const members = [
  { userId: 1, displayName: "나" },
  { userId: 2, displayName: "배우자" },
];

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("TransactionForm", () => {
  test("applies a merchant rule recommendation to a new transaction", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({
          ruleId: 7,
          category: "FOOD",
          tags: ["RECURRING_PAYMENT"],
          source: "MERCHANT_RULE",
        }),
      )
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionForm
        currentUserId={1}
        householdMembers={members}
        onCreated={vi.fn()}
      />,
    );

    await enterRequiredValues(user);
    await screen.findByText("이 가맹점에 저장된 분류를 적용했습니다.");
    expect(
      (screen.getByLabelText("카테고리") as HTMLSelectElement).value,
    ).toBe("FOOD");
    expect(
      (screen.getByLabelText("정기결제") as HTMLInputElement).checked,
    ).toBe(true);

    await user.click(screen.getByRole("button", { name: "거래 저장" }));

    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://localhost:8080/merchant-classification-rules/recommendation?merchant=%EA%B9%80%EB%B0%A5%EC%B2%9C%EA%B5%AD",
    );
    const request = fetchMock.mock.calls[2];
    expect(JSON.parse(String(request[1]?.body))).toMatchObject({
      merchant: "김밥천국",
      category: "FOOD",
      tags: ["RECURRING_PAYMENT"],
      classificationRuleId: 7,
      saveMerchantRule: false,
    });
  });

  test("treats a changed recommendation as user classification and can save a rule", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({
          ruleId: 7,
          category: "FOOD",
          tags: [],
          source: "MERCHANT_RULE",
        }),
      )
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionForm
        currentUserId={1}
        householdMembers={members}
        onCreated={vi.fn()}
      />,
    );

    await enterRequiredValues(user);
    await screen.findByText("이 가맹점에 저장된 분류를 적용했습니다.");
    await user.selectOptions(screen.getByLabelText("카테고리"), "LIVING");
    await user.click(
      screen.getByLabelText(
        "앞으로 이 가맹점에도 같은 카테고리와 태그 적용",
      ),
    );
    await user.click(screen.getByRole("button", { name: "거래 저장" }));

    const request = fetchMock.mock.calls[2];
    expect(JSON.parse(String(request[1]?.body))).toMatchObject({
      category: "LIVING",
      classificationRuleId: null,
      saveMerchantRule: true,
    });
  });
});

async function enterRequiredValues(
  user: ReturnType<typeof userEvent.setup>,
) {
  await user.type(screen.getByLabelText("가맹점"), "김밥천국");
  await user.tab();
  await user.type(screen.getByLabelText("금액"), "8000");
  await user.selectOptions(screen.getByLabelText("카드사"), "SHINHAN");
}

function csrfResponse(): Response {
  return jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" });
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
