import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, test, vi } from "vitest";
import { CardStatementPanel } from "./card-statement-panel";

const preview = {
  importId: 17,
  cardIssuer: "KB_KOOKMIN",
  statementMonth: "2026-07",
  totalCount: 4,
  totalBilledAmount: 132_300,
  adjustmentCount: 2,
  matchedCount: 1,
  missingCount: 1,
  duplicateSuspectedCount: 1,
  mismatchCount: 1,
  candidates: [
    candidate(10, "기존 일치점", "MATCHED", 2_300, [101]),
    candidate(11, "누락 가맹점", "MISSING", 8_000),
    candidate(12, "중복 후보점", "DUPLICATE_SUSPECTED", 20_000, [102, 103]),
    candidate(13, "불일치 후보점", "MISMATCH", 102_000, [104]),
  ],
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("CardStatementPanel", () => {
  test("uploads a statement and applies only the reviewed missing candidate", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(preview))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        jsonResponse({
          transactions: [{ sourceRow: 11, transactionId: 201, created: true }],
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<CardStatementPanel />);

    const file = new File(["xlsx"], "kb-statement.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await user.upload(screen.getByLabelText("KB국민카드 명세서"), file);
    await user.click(screen.getByRole("button", { name: "업로드하고 대조" }));

    expect(await screen.findByRole("heading", { name: "대조 결과" })).toBeDefined();
    expect(screen.getByText("KB국민카드 · 2026년 7월 명세서")).toBeDefined();
    expect(screen.getByText("누락 가맹점")).toBeDefined();
    expect(screen.getByText("중복 후보점")).toBeDefined();
    expect(screen.getByText("불일치 후보점")).toBeDefined();
    expect(screen.queryByText("기존 일치점")).toBeNull();

    const previewRequest = fetchMock.mock.calls[1];
    expect(previewRequest[0]).toBe("http://localhost:8080/card-statements/preview");
    const previewOptions = previewRequest[1];
    expect(previewOptions?.headers).toEqual({ "X-XSRF-TOKEN": "csrf-token" });
    const previewBody = previewOptions?.body as FormData;
    expect((previewBody.get("file") as File).name).toBe("kb-statement.xlsx");

    const saveButton = screen.getByRole("button", {
      name: "선택한 거래 확인하고 저장",
    }) as HTMLButtonElement;
    expect(saveButton.disabled).toBe(true);
    await user.type(screen.getByLabelText("카테고리"), "식비");
    await user.type(screen.getByLabelText(/내역/), "점심");
    expect(saveButton.disabled).toBe(false);
    await user.click(saveButton);

    expect(
      await screen.findByText("1건을 거래 내역에 저장했습니다."),
    ).toBeDefined();
    const applyRequest = fetchMock.mock.calls[3];
    expect(applyRequest[0]).toBe(
      "http://localhost:8080/card-statements/17/apply",
    );
    expect(JSON.parse(String(applyRequest[1]?.body))).toEqual({
      candidates: [
        {
          sourceRow: 11,
          category: "식비",
          description: "점심",
        },
      ],
    });
    expect(screen.queryByText("누락 가맹점")).toBeNull();
  });

  test("shows the server detail when a statement is invalid", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        jsonResponse(
          { detail: "KB국민카드 명세서의 필수 열을 찾을 수 없습니다." },
          400,
        ),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<CardStatementPanel />);

    await user.upload(
      screen.getByLabelText("KB국민카드 명세서"),
      new File(["invalid"], "invalid.xlsx"),
    );
    await user.click(screen.getByRole("button", { name: "업로드하고 대조" }));

    expect((await screen.findByRole("alert")).textContent).toBe(
      "KB국민카드 명세서의 필수 열을 찾을 수 없습니다.",
    );
  });
});

function candidate(
  sourceRow: number,
  merchant: string,
  matchStatus: "MATCHED" | "MISSING" | "DUPLICATE_SUSPECTED" | "MISMATCH",
  approvedAmount: number,
  transactionIds: number[] = [],
) {
  return {
    sourceRow,
    occurredOn: "2026-06-09",
    cardLabel: "국민 테스트카드",
    merchant,
    approvedAmount,
    billedAmount: approvedAmount,
    interestAmount: 0,
    type: "PURCHASE" as const,
    installmentMonths: null,
    installmentSequence: null,
    remainingInstallments: null,
    remainingPrincipal: null,
    matchStatus,
    transactionIds,
  };
}

function csrfResponse(): Response {
  return jsonResponse({ token: "csrf-token", headerName: "X-XSRF-TOKEN" });
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status,
  });
}
