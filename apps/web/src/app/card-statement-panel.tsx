"use client";

import { FormEvent, useState } from "react";

type StatementEntryType = "PURCHASE" | "REVERSAL" | "FEE" | "INSTALLMENT";
type StatementMatchStatus =
  | "MATCHED"
  | "MISSING"
  | "DUPLICATE_SUSPECTED"
  | "MISMATCH";
type CandidateFilter = "REVIEW" | "ALL";

type StatementCandidate = {
  sourceRow: number;
  occurredOn: string;
  cardLabel: string;
  merchant: string;
  approvedAmount: number;
  billedAmount: number;
  interestAmount: number;
  type: StatementEntryType;
  installmentMonths: number | null;
  installmentSequence: number | null;
  remainingInstallments: number | null;
  remainingPrincipal: number | null;
  matchStatus: StatementMatchStatus;
  transactionIds: number[];
};

type CardStatementPreview = {
  importId: number;
  cardIssuer: string;
  statementMonth: string;
  totalCount: number;
  totalBilledAmount: number;
  adjustmentCount: number;
  candidates: StatementCandidate[];
};

type CandidateSelection = {
  selected: boolean;
  category: string;
  description: string;
};

type AppliedStatementTransaction = {
  sourceRow: number;
  transactionId: number;
  created: boolean;
};

type ApplyCardStatementResponse = {
  transactions: AppliedStatementTransaction[];
};

type CsrfToken = {
  token: string;
  headerName: string;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");

export function CardStatementPanel() {
  const [preview, setPreview] = useState<CardStatementPreview | null>(null);
  const [selections, setSelections] = useState<Record<number, CandidateSelection>>({});
  const [filter, setFilter] = useState<CandidateFilter>("REVIEW");
  const [isUploading, setIsUploading] = useState(false);
  const [isApplying, setIsApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fileInput = event.currentTarget.elements.namedItem("statementFile");
    const file = fileInput instanceof HTMLInputElement ? fileInput.files?.[0] : null;
    if (!file) {
      setError("업로드할 명세서 파일을 선택해 주세요.");
      return;
    }
    if (file.size > 2 * 1024 * 1024) {
      setError("명세서 파일은 2MB 이하여야 합니다.");
      return;
    }

    setIsUploading(true);
    setError(null);
    setSuccess(null);
    try {
      const csrfToken = await fetchCsrf("명세서 업로드 요청을 준비하지 못했습니다.");
      const body = new FormData();
      body.append("file", file);
      const response = await fetch(`${apiUrl}/card-statements/preview`, {
        method: "POST",
        credentials: "include",
        headers: { [csrfToken.headerName]: csrfToken.token },
        body,
      });
      if (!response.ok) {
        throw new Error(await responseError(response, "명세서를 처리하지 못했습니다."));
      }
      const nextPreview: CardStatementPreview = await response.json();
      setPreview(nextPreview);
      setSelections(
        Object.fromEntries(
          nextPreview.candidates
            .filter((candidate) => candidate.matchStatus === "MISSING")
            .map((candidate) => [
              candidate.sourceRow,
              { selected: true, category: "", description: "" },
            ]),
        ),
      );
      setFilter("REVIEW");
    } catch (caughtError) {
      setPreview(null);
      setSelections({});
      setError(errorMessage(caughtError, "명세서를 처리하지 못했습니다."));
    } finally {
      setIsUploading(false);
    }
  }

  async function handleApply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!preview) return;

    const selected = preview.candidates
      .filter((candidate) => selections[candidate.sourceRow]?.selected)
      .map((candidate) => ({
        sourceRow: candidate.sourceRow,
        category: selections[candidate.sourceRow].category.trim(),
        description: selections[candidate.sourceRow].description.trim() || null,
      }));
    if (selected.length === 0) {
      setError("저장할 누락 거래를 선택해 주세요.");
      return;
    }
    if (selected.some((candidate) => candidate.category.length === 0)) {
      setError("선택한 누락 거래의 카테고리를 모두 입력해 주세요.");
      return;
    }

    setIsApplying(true);
    setError(null);
    setSuccess(null);
    try {
      const csrfToken = await fetchCsrf("거래 저장 요청을 준비하지 못했습니다.");
      const response = await fetch(`${apiUrl}/card-statements/${preview.importId}/apply`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrfToken.headerName]: csrfToken.token,
        },
        body: JSON.stringify({ candidates: selected }),
      });
      if (!response.ok) {
        throw new Error(await responseError(response, "선택한 거래를 저장하지 못했습니다."));
      }
      const result: ApplyCardStatementResponse = await response.json();
      const transactionByRow = new Map(
        result.transactions.map((transaction) => [transaction.sourceRow, transaction]),
      );
      setPreview((current) =>
        current
          ? {
              ...current,
              candidates: current.candidates.map((candidate) => {
                const applied = transactionByRow.get(candidate.sourceRow);
                return applied
                  ? {
                      ...candidate,
                      matchStatus: "MATCHED",
                      transactionIds: [applied.transactionId],
                    }
                  : candidate;
              }),
            }
          : current,
      );
      setSelections((current) => {
        const next = { ...current };
        result.transactions.forEach((transaction) => {
          delete next[transaction.sourceRow];
        });
        return next;
      });
      setSuccess(`${result.transactions.length}건을 거래 내역에 저장했습니다.`);
    } catch (caughtError) {
      setError(errorMessage(caughtError, "선택한 거래를 저장하지 못했습니다."));
    } finally {
      setIsApplying(false);
    }
  }

  const counts = preview ? statusCounts(preview.candidates) : null;
  const visibleCandidates = preview
    ? preview.candidates.filter(
        (candidate) => filter === "ALL" || candidate.matchStatus !== "MATCHED",
      )
    : [];
  const selectedCandidates = preview
    ? preview.candidates.filter((candidate) => selections[candidate.sourceRow]?.selected)
    : [];
  const hasIncompleteCategory = selectedCandidates.some(
    (candidate) => selections[candidate.sourceRow].category.trim().length === 0,
  );

  return (
    <div className="mx-auto w-full max-w-6xl space-y-8">
      <section className="rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
        <p className="text-sm font-medium text-emerald-700">월말 지출 점검</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">카드 명세서 대조</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-stone-600">
          KB국민카드 XLSX 명세서를 기존 거래와 비교합니다. 원본 파일은 저장하지 않으며,
          선택한 누락 항목만 확인 후 거래 내역에 반영합니다.
        </p>

        <form className="mt-7 flex flex-col gap-4 sm:flex-row sm:items-end" onSubmit={handleUpload}>
          <div className="flex-1">
            <label
              className="mb-2 block text-sm font-medium text-stone-700"
              htmlFor="statementFile"
            >
              KB국민카드 명세서
            </label>
            <input
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              className="block w-full rounded-xl border border-stone-300 bg-white px-4 py-3 text-sm file:mr-4 file:rounded-lg file:border-0 file:bg-stone-100 file:px-3 file:py-2 file:font-medium file:text-stone-700"
              id="statementFile"
              name="statementFile"
              type="file"
            />
            <p className="mt-2 text-xs text-stone-500">XLSX 형식, 최대 2MB</p>
          </div>
          <button
            className="rounded-xl bg-emerald-700 px-6 py-3 font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
            disabled={isUploading}
            type="submit"
          >
            {isUploading ? "대조 중..." : "업로드하고 대조"}
          </button>
        </form>

        {error ? (
          <p className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
            {error}
          </p>
        ) : null}
        {success ? (
          <p
            className="mt-5 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
            role="status"
          >
            {success}
          </p>
        ) : null}
      </section>

      {preview && counts ? (
        <section className="rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-5">
            <div>
              <p className="text-sm font-medium text-emerald-700">
                {cardIssuerLabel(preview.cardIssuer)} · {statementMonthLabel(preview.statementMonth)}
              </p>
              <h2 className="mt-2 text-2xl font-semibold tracking-tight">대조 결과</h2>
              <p className="mt-2 text-sm text-stone-500">
                명세서 {preview.totalCount}건 · 청구 원금{" "}
                {amountFormatter.format(preview.totalBilledAmount)}원 · 조정{" "}
                {preview.adjustmentCount}건
              </p>
            </div>
            <div className="flex rounded-full bg-stone-100 p-1" aria-label="명세서 항목 필터">
              <button
                className={filterButtonClass(filter === "REVIEW")}
                onClick={() => setFilter("REVIEW")}
                type="button"
              >
                검토 필요
              </button>
              <button
                className={filterButtonClass(filter === "ALL")}
                onClick={() => setFilter("ALL")}
                type="button"
              >
                전체
              </button>
            </div>
          </div>

          <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatusSummary label="일치" count={counts.MATCHED} tone="emerald" />
            <StatusSummary label="누락" count={counts.MISSING} tone="red" />
            <StatusSummary
              label="중복 의심"
              count={counts.DUPLICATE_SUSPECTED}
              tone="amber"
            />
            <StatusSummary label="불일치" count={counts.MISMATCH} tone="amber" />
          </div>

          <form className="mt-7" onSubmit={handleApply}>
            {visibleCandidates.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-stone-300 px-6 py-14 text-center">
                <p className="font-medium text-stone-700">검토할 항목이 없습니다.</p>
                <p className="mt-2 text-sm text-stone-500">
                  모든 명세서 거래가 기존 거래와 일치합니다.
                </p>
              </div>
            ) : (
              <ul className="space-y-4">
                {visibleCandidates.map((candidate) => (
                  <CandidateCard
                    candidate={candidate}
                    key={candidate.sourceRow}
                    onSelectionChange={(nextSelection) =>
                      setSelections((current) => ({
                        ...current,
                        [candidate.sourceRow]: nextSelection,
                      }))
                    }
                    selection={selections[candidate.sourceRow]}
                  />
                ))}
              </ul>
            )}

            {counts.MISSING > 0 ? (
              <div className="mt-6 flex flex-wrap items-center justify-between gap-4 border-t border-stone-200 pt-6">
                <p className="text-sm text-stone-600">
                  선택한 누락 거래 {selectedCandidates.length}건을 저장합니다.
                </p>
                <button
                  className="rounded-xl bg-emerald-700 px-6 py-3 font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={
                    isApplying ||
                    selectedCandidates.length === 0 ||
                    hasIncompleteCategory
                  }
                  type="submit"
                >
                  {isApplying ? "저장 중..." : "선택한 거래 확인하고 저장"}
                </button>
              </div>
            ) : null}
          </form>
        </section>
      ) : null}
    </div>
  );
}

function CandidateCard({
  candidate,
  onSelectionChange,
  selection,
}: {
  candidate: StatementCandidate;
  onSelectionChange: (selection: CandidateSelection) => void;
  selection?: CandidateSelection;
}) {
  const canApply = candidate.matchStatus === "MISSING";
  const inputId = `candidate-${candidate.sourceRow}`;

  return (
    <li className="rounded-2xl border border-stone-200 p-5">
      <div className="flex items-start gap-4">
        {canApply && selection ? (
          <input
            aria-label={`${candidate.merchant} 저장 선택`}
            checked={selection.selected}
            className="mt-1 h-5 w-5 rounded border-stone-300 text-emerald-700 accent-emerald-700"
            id={inputId}
            onChange={(event) =>
              onSelectionChange({ ...selection, selected: event.target.checked })
            }
            type="checkbox"
          />
        ) : null}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-medium text-stone-900">{candidate.merchant}</p>
                <StatusBadge status={candidate.matchStatus} />
              </div>
              <p className="mt-1 text-sm text-stone-500">
                {displayDate(candidate.occurredOn)} · {entryTypeLabel(candidate)} ·{" "}
                {candidate.cardLabel}
              </p>
            </div>
            <div className="text-right">
              <p className="font-semibold text-stone-900">
                승인 {amountFormatter.format(candidate.approvedAmount)}원
              </p>
              {candidate.billedAmount !== candidate.approvedAmount ? (
                <p className="mt-1 text-xs text-stone-500">
                  이번 달 청구 {amountFormatter.format(candidate.billedAmount)}원
                </p>
              ) : null}
            </div>
          </div>

          {canApply && selection ? (
            <div className="mt-4 grid gap-4 border-t border-stone-100 pt-4 sm:grid-cols-2">
              <div>
                <label
                  className="mb-2 block text-sm font-medium text-stone-700"
                  htmlFor={`${inputId}-category`}
                >
                  카테고리
                </label>
                <input
                  className="w-full rounded-xl border border-stone-300 px-3 py-2.5 text-sm outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100 disabled:bg-stone-100"
                  disabled={!selection.selected}
                  id={`${inputId}-category`}
                  maxLength={100}
                  onChange={(event) =>
                    onSelectionChange({ ...selection, category: event.target.value })
                  }
                  placeholder="식비"
                  required={selection.selected}
                  value={selection.category}
                />
              </div>
              <div>
                <label
                  className="mb-2 block text-sm font-medium text-stone-700"
                  htmlFor={`${inputId}-description`}
                >
                  내역 <span className="font-normal text-stone-500">(선택)</span>
                </label>
                <input
                  className="w-full rounded-xl border border-stone-300 px-3 py-2.5 text-sm outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100 disabled:bg-stone-100"
                  disabled={!selection.selected}
                  id={`${inputId}-description`}
                  maxLength={500}
                  onChange={(event) =>
                    onSelectionChange({ ...selection, description: event.target.value })
                  }
                  placeholder="사용 목적이나 품목"
                  value={selection.description}
                />
              </div>
            </div>
          ) : candidate.matchStatus === "MATCHED" ? (
            <p className="mt-3 text-xs text-stone-500">기존 거래와 일치해 저장 대상에서 제외됩니다.</p>
          ) : (
            <p className="mt-3 text-xs text-amber-700">
              연결 가능한 기존 거래 {candidate.transactionIds.length}건을 직접 확인해야 합니다.
            </p>
          )}
        </div>
      </div>
    </li>
  );
}

function StatusSummary({
  count,
  label,
  tone,
}: {
  count: number;
  label: string;
  tone: "emerald" | "red" | "amber";
}) {
  const toneClass = {
    emerald: "bg-emerald-50 text-emerald-800",
    red: "bg-red-50 text-red-800",
    amber: "bg-amber-50 text-amber-800",
  }[tone];
  return (
    <div className={`rounded-2xl px-5 py-4 ${toneClass}`}>
      <p className="text-sm">{label}</p>
      <p className="mt-1 text-2xl font-semibold">{count}건</p>
    </div>
  );
}

function StatusBadge({ status }: { status: StatementMatchStatus }) {
  const labels: Record<StatementMatchStatus, string> = {
    MATCHED: "일치",
    MISSING: "누락",
    DUPLICATE_SUSPECTED: "중복 의심",
    MISMATCH: "불일치",
  };
  const classes: Record<StatementMatchStatus, string> = {
    MATCHED: "bg-emerald-50 text-emerald-700",
    MISSING: "bg-red-50 text-red-700",
    DUPLICATE_SUSPECTED: "bg-amber-50 text-amber-700",
    MISMATCH: "bg-amber-50 text-amber-700",
  };
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${classes[status]}`}>
      {labels[status]}
    </span>
  );
}

function statusCounts(candidates: StatementCandidate[]): Record<StatementMatchStatus, number> {
  const counts: Record<StatementMatchStatus, number> = {
    MATCHED: 0,
    MISSING: 0,
    DUPLICATE_SUSPECTED: 0,
    MISMATCH: 0,
  };
  candidates.forEach((candidate) => {
    counts[candidate.matchStatus] += 1;
  });
  return counts;
}

function entryTypeLabel(candidate: StatementCandidate): string {
  if (candidate.type === "INSTALLMENT") {
    const sequence = candidate.installmentSequence
      ? ` ${candidate.installmentSequence}회차`
      : "";
    return `할부${sequence}`;
  }
  const labels: Record<Exclude<StatementEntryType, "INSTALLMENT">, string> = {
    PURCHASE: "일시불",
    REVERSAL: "취소",
    FEE: "연회비",
  };
  return labels[candidate.type];
}

function cardIssuerLabel(cardIssuer: string): string {
  return cardIssuer === "KB_KOOKMIN" ? "KB국민카드" : cardIssuer;
}

function statementMonthLabel(statementMonth: string): string {
  const [year, month] = statementMonth.split("-");
  return `${year}년 ${Number(month)}월 명세서`;
}

function displayDate(value: string): string {
  const [year, month, day] = value.split("-");
  return `${year}.${month}.${day}`;
}

function filterButtonClass(active: boolean): string {
  return `rounded-full px-4 py-2 text-sm font-medium transition ${
    active ? "bg-emerald-700 text-white" : "text-stone-600 hover:bg-stone-200"
  }`;
}

async function fetchCsrf(fallbackMessage: string): Promise<CsrfToken> {
  const response = await fetch(`${apiUrl}/auth/csrf`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(fallbackMessage);
  }
  return response.json();
}

async function responseError(response: Response, fallbackMessage: string): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (
      typeof body === "object" &&
      body !== null &&
      "detail" in body &&
      typeof body.detail === "string"
    ) {
      return body.detail;
    }
  } catch {
    return fallbackMessage;
  }
  return fallbackMessage;
}

function errorMessage(caughtError: unknown, fallbackMessage: string): string {
  return caughtError instanceof Error ? caughtError.message : fallbackMessage;
}
