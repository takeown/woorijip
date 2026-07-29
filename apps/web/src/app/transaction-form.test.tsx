import { cleanup, render, screen, waitFor } from "@testing-library/react";
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

  test("keeps the latest recommendation when an older request fails", async () => {
    const user = userEvent.setup();
    const firstRequest = deferred<Response>();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockReturnValueOnce(firstRequest.promise)
      .mockResolvedValueOnce(
        jsonResponse({
          ruleId: 9,
          category: "LIVING",
          tags: ["UTILITY"],
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

    const merchant = screen.getByLabelText("가맹점");
    await user.type(merchant, "느린가맹점");
    await user.tab();
    await user.click(merchant);
    await user.clear(merchant);
    await user.type(merchant, "최신가맹점");
    await user.tab();
    await screen.findByText("이 가맹점에 저장된 분류를 적용했습니다.");

    firstRequest.reject(new Error("이전 요청 실패"));
    await firstRequest.promise.catch(() => undefined);
    await waitFor(() => {
      expect(
        screen.queryByText("가맹점 분류 추천을 불러오지 못했습니다."),
      ).toBeNull();
    });

    await user.type(screen.getByLabelText("금액"), "8000");
    await user.selectOptions(screen.getByLabelText("카드사"), "SHINHAN");
    await user.click(screen.getByRole("button", { name: "거래 저장" }));

    const request = fetchMock.mock.calls[3];
    expect(JSON.parse(String(request[1]?.body))).toMatchObject({
      merchant: "최신가맹점",
      category: "LIVING",
      tags: ["UTILITY"],
      classificationRuleId: 9,
    });
  });

  test("clears an automatic recommendation when the merchant has no rule", async () => {
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
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionForm
        currentUserId={1}
        householdMembers={members}
        onCreated={vi.fn()}
      />,
    );

    const merchant = screen.getByLabelText("가맹점");
    await user.type(merchant, "규칙가맹점");
    await user.tab();
    await screen.findByText("이 가맹점에 저장된 분류를 적용했습니다.");

    await user.click(merchant);
    await user.clear(merchant);
    await user.type(merchant, "미등록가맹점");
    await user.tab();

    await waitFor(() => {
      expect(
        (screen.getByLabelText("카테고리") as HTMLSelectElement).value,
      ).toBe("");
    });
    expect(
      (screen.getByLabelText("정기결제") as HTMLInputElement).checked,
    ).toBe(false);
  });

  test("keeps a user-edited classification when the merchant changes", async () => {
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
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    render(
      <TransactionForm
        currentUserId={1}
        householdMembers={members}
        onCreated={vi.fn()}
      />,
    );

    const merchant = screen.getByLabelText("가맹점");
    await user.type(merchant, "규칙가맹점");
    await user.tab();
    await screen.findByText("이 가맹점에 저장된 분류를 적용했습니다.");
    await user.selectOptions(screen.getByLabelText("카테고리"), "LIVING");

    await user.click(merchant);
    await user.clear(merchant);
    await user.type(merchant, "미등록가맹점");
    await user.tab();

    await waitFor(() => {
      expect(
        (screen.getByLabelText("카테고리") as HTMLSelectElement).value,
      ).toBe("LIVING");
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, reject, resolve };
}
