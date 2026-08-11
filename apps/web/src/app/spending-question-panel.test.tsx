import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { SpendingQuestionPanel } from "./spending-question-panel";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("SpendingQuestionPanel", () => {
  test("asks a question with csrf and shows the answer evidence", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          status: "ANSWERED",
          message:
            "2026년 8월 식비는 45,000원입니다. 이전 같은 기간보다 5,000원 늘었습니다.",
          evidenceTransactions: [
            {
              id: 10,
              merchant: "우리동네 마트",
              amount: 30_000,
              occurredAt: "2026-08-10T19:30:00+09:00",
              payerLabel: "나",
            },
          ],
          remainingRequestsToday: 19,
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<SpendingQuestionPanel />);
    await user.type(screen.getByLabelText("가계 지출 질문"), "이번 달 식비 얼마 썼어?");
    await user.click(screen.getByRole("button", { name: "질문하기" }));

    expect(
      await screen.findByText(
        "2026년 8월 식비는 45,000원입니다. 이전 같은 기간보다 5,000원 늘었습니다.",
      ),
    ).toBeDefined();
    const evidence = screen.getByRole("heading", { name: "이 답변의 근거" }).parentElement;
    expect(within(evidence as HTMLElement).getByText("우리동네 마트")).toBeDefined();
    expect(within(evidence as HTMLElement).getByText("30,000원")).toBeDefined();
    expect(screen.getByText("오늘 19번 더 질문할 수 있습니다.")).toBeDefined();
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": "csrf-token",
      },
      body: JSON.stringify({ question: "이번 달 식비 얼마 썼어?" }),
    });
  });

  test("fills the input from a suggested question and shows unsupported answers", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(jsonResponse({ token: "csrf", headerName: "X-XSRF-TOKEN" }))
        .mockResolvedValueOnce(
          jsonResponse({
            status: "UNSUPPORTED",
            message: "현재는 기간별 총지출, 카테고리 지출과 가장 큰 지출만 답할 수 있습니다.",
            evidenceTransactions: [],
            remainingRequestsToday: 18,
          }),
        ),
    );

    render(<SpendingQuestionPanel />);
    await user.click(screen.getByRole("button", { name: "이번 달 가장 큰 지출은 뭐야?" }));
    expect((screen.getByLabelText("가계 지출 질문") as HTMLInputElement).value).toBe(
      "이번 달 가장 큰 지출은 뭐야?",
    );
    await user.click(screen.getByRole("button", { name: "질문하기" }));

    expect(
      await screen.findByText(
        "현재는 기간별 총지출, 카테고리 지출과 가장 큰 지출만 답할 수 있습니다.",
      ),
    ).toBeDefined();
    expect(screen.queryByRole("heading", { name: "이 답변의 근거" })).toBeNull();
  });

  test("shows the api problem detail when the daily limit is exhausted", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi
        .fn<typeof fetch>()
        .mockResolvedValueOnce(jsonResponse({ token: "csrf", headerName: "X-XSRF-TOKEN" }))
        .mockResolvedValueOnce(
          jsonResponse(
            { detail: "오늘 사용할 수 있는 가계 질문 20회를 모두 사용했습니다." },
            429,
          ),
        ),
    );

    render(<SpendingQuestionPanel />);
    await user.type(screen.getByLabelText("가계 지출 질문"), "이번 달 식비");
    await user.click(screen.getByRole("button", { name: "질문하기" }));

    expect((await screen.findByRole("alert")).textContent).toContain(
      "오늘 사용할 수 있는 가계 질문 20회를 모두 사용했습니다.",
    );
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}
