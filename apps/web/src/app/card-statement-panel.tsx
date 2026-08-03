"use client";

import { FormEvent, useState } from "react";
import {
  categoryLabel,
  transactionCategories,
  transactionTags,
  type TransactionCategory,
  type TransactionTag,
} from "./transaction-classification";

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
  storedValueAccountType: "ONNURI_GIFT_CERTIFICATE" | "PREGNANCY_VOUCHER" | null;
  matchStatus: StatementMatchStatus;
  transactionIds: number[];
  relatedTransactions: RelatedTransaction[];
};

type RelatedTransaction = {
  id: number;
  merchant: string;
  description: string | null;
  amount: number;
  category: TransactionCategory;
  tags: TransactionTag[];
  occurredAt: string;
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
  category: TransactionCategory | "";
  tags: TransactionTag[];
  description: string;
};

type AppliedStatementTransaction = {
  sourceRow: number;
  transactionId: number;
  created: boolean;
  updated: boolean;
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
  const [corrections, setCorrections] = useState<Record<number, boolean>>({});
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
              { selected: true, category: "", tags: [], description: "" },
            ]),
        ),
      );
      setCorrections(
        Object.fromEntries(
          nextPreview.candidates
            .filter(
              (candidate) =>
                candidate.matchStatus === "MISMATCH" &&
                candidate.relatedTransactions.length === 1,
            )
            .map((candidate) => [candidate.sourceRow, false]),
        ),
      );
      setFilter("REVIEW");
    } catch (caughtError) {
      setPreview(null);
      setSelections({});
      setCorrections({});
      setError(errorMessage(caughtError, "명세서를 처리하지 못했습니다."));
    } finally {
      setIsUploading(false);
    }
  }

  async function handleApply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!preview) return;

    const selected = preview.candidates
      .filter(
        (candidate) =>
          candidate.matchStatus === "MISSING" &&
          selections[candidate.sourceRow]?.selected,
      )
      .map((candidate) => ({
        sourceRow: candidate.sourceRow,
        category: selections[candidate.sourceRow].category,
        tags: selections[candidate.sourceRow].tags,
        description: selections[candidate.sourceRow].description.trim() || null,
      }));
    const selectedCorrections = preview.candidates
      .filter(
        (candidate) =>
          candidate.matchStatus === "MISMATCH" &&
          corrections[candidate.sourceRow] &&
          candidate.relatedTransactions.length === 1,
      )
      .map((candidate) => {
        const relatedTransaction = candidate.relatedTransactions[0];
        return {
          sourceRow: candidate.sourceRow,
          transactionId: relatedTransaction.id,
          expectedMerchant: relatedTransaction.merchant,
          expectedAmount: relatedTransaction.amount,
        };
      });
    if (selected.length === 0 && selectedCorrections.length === 0) {
      setError("반영할 명세서 변경 사항을 선택해 주세요.");
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
        body: JSON.stringify({
          candidates: selected,
          corrections: selectedCorrections,
        }),
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
                      relatedTransactions: candidate.relatedTransactions.map(
                        (transaction) =>
                          transaction.id === applied.transactionId
                            ? {
                                ...transaction,
                                merchant: candidate.merchant,
                                amount: candidate.approvedAmount,
                              }
                            : transaction,
                      ),
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
      setCorrections((current) => {
        const next = { ...current };
        result.transactions.forEach((transaction) => {
          delete next[transaction.sourceRow];
        });
        return next;
      });
      setSuccess(`${result.transactions.length}건의 변경 사항을 반영했습니다.`);
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
    ? preview.candidates.filter(
        (candidate) =>
          candidate.matchStatus === "MISSING" &&
          selections[candidate.sourceRow]?.selected,
      )
    : [];
  const selectedCorrectionCount = preview
    ? preview.candidates.filter(
        (candidate) =>
          candidate.matchStatus === "MISMATCH" && corrections[candidate.sourceRow],
      ).length
    : 0;
  const selectedChangeCount = selectedCandidates.length + selectedCorrectionCount;
  const hasIncompleteCategory = selectedCandidates.some(
    (candidate) => selections[candidate.sourceRow].category.trim().length === 0,
  );

  return (
    <div className="mx-auto w-full max-w-6xl space-y-8">
      <section className="rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
        <p className="text-sm font-medium text-emerald-700">월말 지출 점검</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">카드 명세서 대조</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-stone-600">
          KB국민카드 XLSX 또는 현대카드 XLS 명세서를 기존 거래와 비교합니다. 원본 파일은 저장하지 않으며,
          선택한 누락과 불일치 보정만 확인 후 거래 내역에 반영합니다.
        </p>

        <form className="mt-7" onSubmit={handleUpload}>
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label
                className="mb-2 block text-sm font-medium text-stone-700"
                htmlFor="statementFile"
              >
                카드 명세서
              </label>
              <input
                accept=".xls,.xlsx,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                className="block w-full rounded-xl border border-stone-300 bg-white px-4 py-3 text-sm file:mr-4 file:rounded-lg file:border-0 file:bg-stone-100 file:px-3 file:py-2 file:font-medium file:text-stone-700"
                id="statementFile"
                name="statementFile"
                type="file"
              />
            </div>
            <button
              className="w-full rounded-xl bg-emerald-700 px-6 py-3 font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
              disabled={isUploading}
              type="submit"
            >
              {isUploading ? "대조 중..." : "업로드하고 대조"}
            </button>
          </div>
          <p className="mt-2 text-xs text-stone-500">KB국민카드 XLSX·현대카드 XLS, 최대 2MB</p>
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
                    correctionSelected={corrections[candidate.sourceRow] ?? false}
                    onCorrectionChange={(selected) =>
                      setCorrections((current) => ({
                        ...current,
                        [candidate.sourceRow]: selected,
                      }))
                    }
                    selection={selections[candidate.sourceRow]}
                  />
                ))}
              </ul>
            )}

            {counts.MISSING > 0 || counts.MISMATCH > 0 ? (
              <div className="mt-6 flex flex-wrap items-center justify-between gap-4 border-t border-stone-200 pt-6">
                <p className="text-sm text-stone-600">
                  선택한 변경 사항 {selectedChangeCount}건을 반영합니다.
                </p>
                <button
                  className="rounded-xl bg-emerald-700 px-6 py-3 font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={
                    isApplying ||
                    selectedChangeCount === 0 ||
                    hasIncompleteCategory
                  }
                  type="submit"
                >
                  {isApplying ? "반영 중..." : "선택한 변경 확인하고 반영"}
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
  correctionSelected,
  onCorrectionChange,
  onSelectionChange,
  selection,
}: {
  candidate: StatementCandidate;
  correctionSelected: boolean;
  onCorrectionChange: (selected: boolean) => void;
  onSelectionChange: (selection: CandidateSelection) => void;
  selection?: CandidateSelection;
}) {
  const canApply = candidate.matchStatus === "MISSING";
  const canCorrect =
    candidate.matchStatus === "MISMATCH" &&
    candidate.relatedTransactions.length === 1;
  const relatedTransaction = canCorrect ? candidate.relatedTransactions[0] : null;
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
        ) : canCorrect ? (
          <input
            aria-label={`${candidate.merchant} 명세서 기준 수정 선택`}
            checked={correctionSelected}
            className="mt-1 h-5 w-5 rounded border-stone-300 text-emerald-700 accent-emerald-700"
            id={inputId}
            onChange={(event) => onCorrectionChange(event.target.checked)}
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
              {candidate.storedValueAccountType === "ONNURI_GIFT_CERTIFICATE" ? (
                <p className="mt-2 text-xs font-medium text-emerald-700">
                  온누리상품권 잔액 사용 · 반영 시 잔액에서 차감
                </p>
              ) : null}
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
                <select
                  className="w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100 disabled:bg-stone-100"
                  disabled={!selection.selected}
                  id={`${inputId}-category`}
                  onChange={(event) =>
                    onSelectionChange({
                      ...selection,
                      category: event.target.value as TransactionCategory,
                    })
                  }
                  required={selection.selected}
                  value={selection.category}
                >
                  <option disabled value="">카테고리를 선택해 주세요</option>
                  {transactionCategories.map((category) => (
                    <option key={category.value} value={category.value}>
                      {category.label} — {category.examples.slice(0, 3).join("·")}
                    </option>
                  ))}
                </select>
                {selection.category ? (
                  <p className="mt-2 text-xs leading-5 text-stone-500">
                    {transactionCategories
                      .find((category) => category.value === selection.category)
                      ?.examples.join(", ")}
                  </p>
                ) : null}
                <div className="mt-3 flex flex-wrap gap-2">
                  {transactionTags.map((tag) => (
                    <label
                      className="flex items-center gap-1.5 text-xs text-stone-600"
                      key={tag.value}
                    >
                      <input
                        checked={selection.tags.includes(tag.value)}
                        disabled={!selection.selected}
                        onChange={(event) =>
                          onSelectionChange({
                            ...selection,
                            tags: event.target.checked
                              ? [...selection.tags, tag.value]
                              : selection.tags.filter((value) => value !== tag.value),
                          })
                        }
                        type="checkbox"
                      />
                      {tag.label}
                    </label>
                  ))}
                </div>
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
          ) : relatedTransaction ? (
            <div className="mt-4 border-t border-stone-100 pt-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-xl bg-stone-50 p-4">
                  <p className="text-xs font-medium text-stone-500">현재 거래</p>
                  <p className="mt-2 text-sm font-medium text-stone-800">
                    {relatedTransaction.merchant}
                  </p>
                  <p className="mt-1 text-sm text-stone-600">
                    {amountFormatter.format(relatedTransaction.amount)}원 ·{" "}
                    {categoryLabel(relatedTransaction.category)}
                  </p>
                </div>
                <div className="rounded-xl bg-amber-50 p-4">
                  <p className="text-xs font-medium text-amber-700">명세서 기준</p>
                  <p className="mt-2 text-sm font-medium text-stone-800">
                    {candidate.merchant}
                  </p>
                  <p className="mt-1 text-sm text-stone-600">
                    {amountFormatter.format(candidate.approvedAmount)}원
                  </p>
                </div>
              </div>
              <p className="mt-3 text-xs text-stone-500">
                선택하면 가맹점과 금액만 수정하며 카테고리, 내역, 결제 시각은 유지합니다.
              </p>
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
  if (cardIssuer === "KB_KOOKMIN") return "KB국민카드";
  if (cardIssuer === "HYUNDAI") return "현대카드";
  return cardIssuer;
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
