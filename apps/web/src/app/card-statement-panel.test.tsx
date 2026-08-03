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
    {
      ...candidate(11, "누락 가맹점", "MISSING", 8_000),
      storedValueAccountType: "ONNURI_GIFT_CERTIFICATE",
    },
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
          transactions: [
            { sourceRow: 11, transactionId: 201, created: true, updated: false },
          ],
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<CardStatementPanel />);

    const file = new File(["xlsx"], "kb-statement.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    await user.upload(screen.getByLabelText("카드 명세서"), file);
    await user.click(screen.getByRole("button", { name: "업로드하고 대조" }));

    expect(await screen.findByRole("heading", { name: "대조 결과" })).toBeDefined();
    expect(screen.getByText("KB국민카드 · 2026년 7월 명세서")).toBeDefined();
    expect(screen.getByText("누락 가맹점")).toBeDefined();
    expect(screen.getByText("온누리상품권 잔액 사용 · 반영 시 잔액에서 차감")).toBeDefined();
    expect(screen.getByText("중복 후보점")).toBeDefined();
    expect(screen.getAllByText("불일치 후보점")).toHaveLength(2);
    expect(screen.queryByText("기존 일치점")).toBeNull();

    const previewRequest = fetchMock.mock.calls[1];
    expect(previewRequest[0]).toBe("http://localhost:8080/card-statements/preview");
    const previewOptions = previewRequest[1];
    expect(previewOptions?.headers).toEqual({ "X-XSRF-TOKEN": "csrf-token" });
    const previewBody = previewOptions?.body as FormData;
    expect((previewBody.get("file") as File).name).toBe("kb-statement.xlsx");

    const saveButton = screen.getByRole("button", {
      name: "선택한 변경 확인하고 반영",
    }) as HTMLButtonElement;
    expect(saveButton.disabled).toBe(true);
    await user.selectOptions(screen.getByLabelText("카테고리"), "FOOD");
    await user.type(screen.getByLabelText(/내역/), "점심");
    expect(saveButton.disabled).toBe(false);
    await user.click(saveButton);

    expect(
      await screen.findByText("1건의 변경 사항을 반영했습니다."),
    ).toBeDefined();
    const applyRequest = fetchMock.mock.calls[3];
    expect(applyRequest[0]).toBe(
      "http://localhost:8080/card-statements/17/apply",
    );
    expect(JSON.parse(String(applyRequest[1]?.body))).toEqual({
      candidates: [
        {
          sourceRow: 11,
          category: "FOOD",
          tags: [],
          description: "점심",
        },
      ],
      corrections: [],
    });
    expect(screen.queryByText("누락 가맹점")).toBeNull();
  });

  test("shows the mismatch comparison and sends the confirmed correction", async () => {
    const user = userEvent.setup();
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(preview))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(
        jsonResponse({
          transactions: [
            { sourceRow: 13, transactionId: 104, created: false, updated: true },
          ],
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    render(<CardStatementPanel />);
    await user.upload(
      screen.getByLabelText("카드 명세서"),
      new File(["xlsx"], "kb-statement.xlsx"),
    );
    await user.click(screen.getByRole("button", { name: "업로드하고 대조" }));

    expect(await screen.findByText("현재 거래")).toBeDefined();
    expect(screen.getByText("기존 불일치점")).toBeDefined();
    expect(screen.getByText("명세서 기준")).toBeDefined();

    await user.click(screen.getByLabelText("누락 가맹점 저장 선택"));
    await user.click(
      screen.getByLabelText("불일치 후보점 명세서 기준 수정 선택"),
    );
    await user.click(
      screen.getByRole("button", { name: "선택한 변경 확인하고 반영" }),
    );

    expect(
      await screen.findByText("1건의 변경 사항을 반영했습니다."),
    ).toBeDefined();
    expect(JSON.parse(String(fetchMock.mock.calls[3][1]?.body))).toEqual({
      candidates: [],
      corrections: [
        {
          sourceRow: 13,
          transactionId: 104,
          expectedMerchant: "기존 불일치점",
          expectedAmount: 102_000,
        },
      ],
    });
    expect(screen.queryByText("불일치 후보점")).toBeNull();
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
      screen.getByLabelText("카드 명세서"),
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
    storedValueAccountType: null,
    matchStatus,
    transactionIds,
    relatedTransactions: transactionIds.map((transactionId, index) => ({
      id: transactionId,
      merchant:
        matchStatus === "MISMATCH"
          ? "기존 불일치점"
          : index === 0
            ? merchant
            : `${merchant} ${index + 1}`,
      description: null,
      amount: approvedAmount,
      category: "LIVING",
      tags: [],
      occurredAt: "2026-06-09T12:00:00+09:00",
    })),
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
