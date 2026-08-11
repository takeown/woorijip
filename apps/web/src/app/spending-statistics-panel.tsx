/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V5 */
/* Hallmark · genre: modern-minimal · macrostructure: Workbench
 * theme: Woorijip custom · tone: soft + utilitarian · designed-as-app
 */
"use client";

import { useEffect, useState } from "react";
import { SpendingQuestionPanel } from "./spending-question-panel";

type SpendingPeriod = "DAY" | "WEEK" | "MONTH";
type SpendingPayer = "ALL" | "ME" | "PARTNER";

type PeriodSummary = {
  totalAmount: number;
  coupleLivingAmount: number;
  childcareAmount: number;
  transactionCount: number;
};

type BreakdownItem = {
  key: string;
  label: string;
  amount: number;
  transactionCount: number;
};

type ComparisonBreakdownItem = {
  key: string;
  label: string;
  currentAmount: number;
  currentTransactionCount: number;
  previousAmount: number;
  previousTransactionCount: number;
  amountChange: number;
  changeRatePercent: number | null;
};

type RecurringSpendingChange = {
  tag: "SUBSCRIPTION" | "UTILITY" | "RECURRING_PAYMENT";
  label: string;
  direction: "NEW" | "INCREASED" | "DECREASED" | "ENDED";
  currentAmount: number;
  previousAmount: number;
  amountChange: number;
  message: string;
};

type SpendingEvidenceTransaction = {
  id: number;
  merchant: string;
  amount: number;
  occurredAt: string;
  payerLabel: string;
};

type MonthlySpendingSummary = {
  topCategory: BreakdownItem;
  sharePercent: number;
  categoryAmountChange: number;
  categoryChangeRatePercent: number | null;
  evidenceTransactions: SpendingEvidenceTransaction[];
};

type SpendingStatistics = {
  period: SpendingPeriod;
  payer: SpendingPayer;
  referenceDate: string;
  startDate: string;
  endDateExclusive: string;
  current: PeriodSummary;
  previous: PeriodSummary;
  amountChange: number;
  changeRatePercent: number | null;
  byPayer: BreakdownItem[];
  byPaymentMethod: BreakdownItem[];
  byCategory: BreakdownItem[];
  categoryComparisons: ComparisonBreakdownItem[];
  tagComparisons: ComparisonBreakdownItem[];
  recurringSpendingChanges?: RecurringSpendingChange[];
  monthlySummary?: MonthlySpendingSummary | null;
};

type SpendingStatisticsPanelProps = {
  refreshKey: number;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");
const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeZone: "UTC",
});
const monthFormatter = new Intl.DateTimeFormat("ko-KR", {
  month: "long",
  timeZone: "UTC",
  year: "numeric",
});
const evidenceDateFormatter = new Intl.DateTimeFormat("ko-KR", {
  day: "numeric",
  month: "long",
  timeZone: "Asia/Seoul",
});

export function SpendingStatisticsPanel({ refreshKey }: SpendingStatisticsPanelProps) {
  const [period, setPeriod] = useState<SpendingPeriod>("MONTH");
  const [payer, setPayer] = useState<SpendingPayer>("ALL");
  const [referenceDate, setReferenceDate] = useState(seoulToday);
  const requestKey = `${period}:${payer}:${referenceDate}:${refreshKey}`;
  const requestUrl = `${apiUrl}/statistics/spending?period=${period}&payer=${payer}&date=${referenceDate}&includeMonthlySummary=true`;
  const [loadResult, setLoadResult] = useState<{
    requestKey: string;
    statistics: SpendingStatistics | null;
    error: string | null;
  }>({ requestKey: "", statistics: null, error: null });
  const isLoading = loadResult.requestKey !== requestKey;
  const statistics = isLoading ? null : loadResult.statistics;
  const error = isLoading ? null : loadResult.error;

  useEffect(() => {
    const controller = new AbortController();

    fetch(requestUrl, {
      cache: "no-store",
      credentials: "include",
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error("지출 통계를 불러오지 못했습니다.");
        }
        setLoadResult({
          requestKey,
          statistics: await response.json(),
          error: null,
        });
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        setLoadResult({
          requestKey,
          statistics: null,
          error:
            caughtError instanceof Error
              ? caughtError.message
              : "지출 통계를 불러오지 못했습니다.",
        });
      });

    return () => controller.abort();
  }, [requestKey, requestUrl]);

  function movePeriod(direction: -1 | 1) {
    const date = parseDate(referenceDate);
    if (period === "DAY") date.setUTCDate(date.getUTCDate() + direction);
    if (period === "WEEK") date.setUTCDate(date.getUTCDate() + direction * 7);
    if (period === "MONTH") {
      date.setUTCDate(1);
      date.setUTCMonth(date.getUTCMonth() + direction);
    }
    setReferenceDate(toDateInputValue(date));
  }

  return (
    <section className="mx-auto mb-8 w-full max-w-6xl">
      <header className="max-w-2xl">
        <h2 className="min-w-0 text-3xl font-semibold tracking-tight [overflow-wrap:anywhere]">
          기간별 통계
        </h2>
        <p className="mt-2 text-sm text-stone-600">
          우리집 지출이 어디에서 달라졌는지 서울 시간 기준으로 살펴봅니다.
        </p>
      </header>

      <SpendingQuestionPanel />

      <div className="mt-6 border-y border-border-soft py-4">
        <div className="grid gap-4 lg:grid-cols-[auto_auto_minmax(0,1fr)] lg:items-end">
          <div>
            <p className="mb-2 text-xs font-medium text-stone-600">기간</p>
            <div
              className="flex items-center rounded-full bg-surface-muted p-1"
              aria-label="통계 기간"
              role="group"
            >
              {([
                ["DAY", "일간"],
                ["WEEK", "주간"],
                ["MONTH", "월간"],
              ] as const).map(([value, label]) => (
                <button
                  aria-pressed={period === value}
                  className={`min-h-11 whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors duration-150 ease-[var(--ease-out)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink disabled:cursor-not-allowed disabled:opacity-50 ${
                    period === value
                      ? "bg-accent text-accent-ink active:bg-accent-strong"
                      : "text-stone-700 hover:bg-accent-soft active:bg-accent-soft"
                  }`}
                  key={value}
                  onClick={() => setPeriod(value)}
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-medium text-stone-600">결제자</p>
            <div
              className="flex items-center rounded-full bg-surface-muted p-1"
              aria-label="통계 결제자"
              role="group"
            >
              {([
                ["ALL", "전체"],
                ["ME", "나"],
                ["PARTNER", "배우자"],
              ] as const).map(([value, label]) => (
                <button
                  aria-pressed={payer === value}
                  className={`min-h-11 whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors duration-150 ease-[var(--ease-out)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink disabled:cursor-not-allowed disabled:opacity-50 ${
                    payer === value
                      ? "bg-accent text-accent-ink active:bg-accent-strong"
                      : "text-stone-700 hover:bg-accent-soft active:bg-accent-soft"
                  }`}
                  key={value}
                  onClick={() => setPayer(value)}
                  type="button"
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="lg:justify-self-end">
            <p className="mb-2 text-xs font-medium text-stone-600">기준 날짜</p>
            <div className="flex flex-wrap items-center gap-2">
              <button
                aria-label="이전 기간"
                className="min-h-11 min-w-11 rounded-xl border border-border-soft bg-surface px-3 text-stone-700 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"
                onClick={() => movePeriod(-1)}
                type="button"
              >
                ←
              </button>
              <input
                aria-label="통계 기준 날짜"
                className="min-h-11 min-w-0 rounded-xl border border-border-soft bg-surface px-3 font-ui text-sm tabular-nums outline-2 outline-offset-2 outline-transparent transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50"
                onChange={(event) => setReferenceDate(event.target.value)}
                type="date"
                value={referenceDate}
              />
              <button
                aria-label="다음 기간"
                className="min-h-11 min-w-11 rounded-xl border border-border-soft bg-surface px-3 text-stone-700 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"
                onClick={() => movePeriod(1)}
                type="button"
              >
                →
              </button>
              <button
                className="min-h-11 whitespace-nowrap rounded-xl px-3 text-sm font-medium text-accent-strong transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
                onClick={() => setReferenceDate(seoulToday())}
                type="button"
              >
                오늘
              </button>
            </div>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="mt-8 border-y border-border-soft bg-surface-muted px-5 py-12 text-center text-stone-600" role="status">
          통계를 계산하고 있습니다.
        </div>
      ) : error ? (
        <p className="mt-8 rounded-xl bg-red-50 px-5 py-4 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : statistics ? (
        <>
          <div className="mt-8 grid border-y border-border-soft lg:grid-cols-[minmax(0,1.35fr)_minmax(16rem,0.65fr)]">
            <div className="py-7 lg:border-r lg:border-border-soft lg:pr-8">
              <div>
                <p className="text-sm text-stone-600">총지출</p>
                <p className="mt-2 font-ui text-4xl font-semibold tracking-tight text-foreground tabular-nums sm:text-5xl">
                  {amountFormatter.format(statistics.current.totalAmount)}원
                </p>
              </div>
              <p
                className={`mt-4 font-ui text-sm tabular-nums ${
                  statistics.amountChange > 0 ? "text-amber-700" : "text-stone-600"
                }`}
              >
                <span className="mr-2 font-sans text-stone-600">이전 기간 대비</span>
                <span>{comparisonLabel(statistics)}</span>
              </p>
              <p className="mt-2 font-ui text-sm text-stone-600 tabular-nums">
                {periodLabel(statistics)}
              </p>
            </div>

            <dl className="grid grid-cols-3 border-t border-border-soft py-5 lg:grid-cols-1 lg:border-t-0 lg:py-3 lg:pl-8">
              <SummaryFact
                label="거래 건수"
                value={`${amountFormatter.format(statistics.current.transactionCount)}건`}
              />
              <SummaryFact
                label="부부 생활비"
                value={`${amountFormatter.format(statistics.current.coupleLivingAmount)}원`}
              />
              <SummaryFact
                label="육아비"
                value={`${amountFormatter.format(statistics.current.childcareAmount)}원`}
              />
            </dl>
          </div>

          {statistics.period === "MONTH" && statistics.monthlySummary ? (
            <MonthlySummary startDate={statistics.startDate} summary={statistics.monthlySummary} />
          ) : null}

          {statistics.current.transactionCount === 0 ? (
            <div className="mt-8 border-y border-border-soft py-10 text-left">
              <p className="font-medium text-stone-700">이 기간에는 기록된 지출이 없습니다.</p>
              <p className="mt-2 text-sm text-stone-600">다른 날짜나 기간을 선택해 보세요.</p>
            </div>
          ) : (
            <div
              className={`mt-8 grid gap-8 ${
                statistics.payer === "ALL" ? "lg:grid-cols-2" : ""
              }`}
            >
              {statistics.payer === "ALL" ? (
                <Breakdown
                  title="결제자"
                  items={statistics.byPayer}
                  total={statistics.current.totalAmount}
                />
              ) : null}
              <Breakdown
                title="결제수단"
                items={statistics.byPaymentMethod}
                total={statistics.current.totalAmount}
              />
            </div>
          )}

          <RecurringSpendingChanges changes={statistics.recurringSpendingChanges ?? []} />

          {statistics.categoryComparisons.length > 0 || statistics.tagComparisons.length > 0 ? (
            <div className="mt-8 grid gap-8 lg:grid-cols-2">
              <ComparisonBreakdown
                items={statistics.categoryComparisons}
                title="카테고리 비교"
              />
              <ComparisonBreakdown
                description="태그는 서로 겹칠 수 있어 합계가 총지출과 다를 수 있습니다."
                emptyMessage="비교할 태그 지출이 없습니다."
                items={statistics.tagComparisons}
                title="태그 비교"
              />
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function MonthlySummary({
  startDate,
  summary,
}: {
  startDate: string;
  summary: MonthlySpendingSummary;
}) {
  return (
    <section
      aria-labelledby="monthly-spending-summary-title"
      className="mt-8 border-y border-border-soft bg-accent-soft px-4 py-6 sm:px-5"
    >
      <h3 className="text-xl font-semibold text-foreground" id="monthly-spending-summary-title">
        {monthlyQuestionLabel(startDate)}
      </h3>
      <p className="mt-3 text-lg font-semibold text-stone-900">
        {summary.topCategory.label}에 가장 많이 썼어요.
      </p>
      <p className="mt-1 font-ui text-sm text-stone-700 tabular-nums">
        {amountFormatter.format(summary.topCategory.amount)}원 · 전체의 {summary.sharePercent}%
      </p>
      <p className="mt-2 text-sm text-stone-700">{categoryChangeLabel(summary)}</p>

      <div className="mt-5 border-t border-border-soft pt-4">
        <h4 className="text-sm font-semibold text-stone-900">이 설명의 근거</h4>
        <ul className="mt-2 divide-y divide-border-soft">
          {summary.evidenceTransactions.map((transaction) => (
            <li className="flex items-baseline justify-between gap-4 py-3" key={transaction.id}>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-stone-800">{transaction.merchant}</p>
                <p className="mt-1 font-ui text-xs text-stone-600 tabular-nums">
                  {evidenceDateFormatter.format(new Date(transaction.occurredAt))} · {transaction.payerLabel}
                </p>
              </div>
              <p className="shrink-0 font-ui text-sm font-semibold text-stone-900 tabular-nums">
                {amountFormatter.format(transaction.amount)}원
              </p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function monthlyQuestionLabel(startDate: string): string {
  if (startDate.slice(0, 7) === seoulToday().slice(0, 7)) {
    return "이번 달 돈 어디 갔어?";
  }
  return `${monthFormatter.format(parseDate(startDate))} 돈 어디 갔어?`;
}

function categoryChangeLabel(summary: MonthlySpendingSummary): string {
  if (summary.categoryAmountChange === summary.topCategory.amount) {
    return "지난달에는 이 분류 지출이 없었어요.";
  }
  if (summary.categoryAmountChange === 0) {
    return "지난달과 같은 금액이에요.";
  }

  const direction = summary.categoryAmountChange > 0 ? "늘었어요" : "줄었어요";
  return `지난달보다 ${amountFormatter.format(Math.abs(summary.categoryAmountChange))}원 ${direction}.`;
}

function RecurringSpendingChanges({
  changes,
}: {
  changes: RecurringSpendingChange[];
}) {
  return (
    <section className="mt-8 border-y border-border-soft bg-surface-muted px-4 py-5 sm:px-5">
      <h3 className="font-semibold text-foreground">반복 지출 변화</h3>
      <p className="mt-1 text-xs text-stone-700">
        확정된 구독·공과금·정기결제 태그를 이전 기간과 비교했습니다.
      </p>
      {changes.length === 0 ? (
        <p className="mt-4 text-sm text-stone-700">
          이전 기간과 달라진 반복 지출이 없습니다.
        </p>
      ) : (
        <ul className="mt-4 divide-y divide-border-soft">
          {changes.map((change) => (
            <li className="py-3 text-sm text-stone-700 first:pt-0 last:pb-0" key={change.tag}>
              {change.message}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function ComparisonBreakdown({
  title,
  items,
  description,
  emptyMessage = "비교할 지출이 없습니다.",
}: {
  title: string;
  items: ComparisonBreakdownItem[];
  description?: string;
  emptyMessage?: string;
}) {
  return (
    <section className="border-t border-border-soft pt-5">
      <h3 className="font-semibold text-stone-900">{title}</h3>
      {description ? <p className="mt-1 text-xs text-stone-600">{description}</p> : null}
      {items.length === 0 ? (
        <p className="mt-4 text-sm text-stone-600">{emptyMessage}</p>
      ) : (
        <ul className="mt-4 divide-y divide-border-soft">
          {items.map((item) => (
            <li className="py-3 first:pt-0 last:pb-0" key={item.key}>
              <div className="flex items-baseline justify-between gap-3">
                <span className="min-w-0 [overflow-wrap:anywhere] font-medium text-stone-700">
                  {item.label}
                </span>
                <span className="shrink-0 font-ui font-semibold text-stone-900 tabular-nums">
                  {amountFormatter.format(item.currentAmount)}원
                </span>
              </div>
              <div className="mt-1 flex flex-wrap justify-between gap-x-3 gap-y-1 font-ui text-xs tabular-nums">
                <span className="text-stone-600">{item.currentTransactionCount}건</span>
                <span className={item.amountChange > 0 ? "text-amber-700" : "text-stone-600"}>
                  {breakdownComparisonLabel(item)}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function SummaryFact({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="min-w-0 px-2 first:pl-0 last:pr-0 lg:border-b lg:border-border-soft lg:px-0 lg:py-4 lg:first:pt-0 lg:last:border-b-0 lg:last:pb-0">
      <dt className="text-xs text-stone-600">{label}</dt>
      <dd className="mt-1 font-ui text-sm font-semibold text-stone-900 tabular-nums sm:text-base">
        {value}
      </dd>
    </div>
  );
}

function Breakdown({
  title,
  items,
  total,
}: {
  title: string;
  items: BreakdownItem[];
  total: number;
}) {
  return (
    <section className="border-t border-border-soft pt-5">
      <h3 className="font-semibold text-stone-900">{title}</h3>
      <ul className="mt-4 space-y-4">
        {items.map((item) => (
          <li key={item.key}>
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="min-w-0 truncate text-stone-700">{item.label}</span>
              <span className="shrink-0 font-ui font-medium text-stone-900 tabular-nums">
                {amountFormatter.format(item.amount)}원
              </span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-surface-muted">
              <div
                className="h-full rounded-full bg-accent"
                style={{ width: `${total === 0 ? 0 : (item.amount / total) * 100}%` }}
              />
            </div>
            <p className="mt-1 font-ui text-xs text-stone-600 tabular-nums">
              {item.transactionCount}건
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
}

function comparisonLabel(statistics: SpendingStatistics): string {
  if (statistics.previous.totalAmount === 0) {
    return statistics.current.totalAmount === 0 ? "변화 없음" : "이전 지출 없음";
  }
  if (statistics.amountChange === 0) return "변화 없음";

  const direction = statistics.amountChange > 0 ? "증가" : "감소";
  const rate = Math.abs(statistics.changeRatePercent ?? 0);
  return `${amountFormatter.format(Math.abs(statistics.amountChange))}원 ${direction} (${rate}%)`;
}

function breakdownComparisonLabel(item: ComparisonBreakdownItem): string {
  if (item.previousAmount === 0) {
    return item.currentAmount === 0 ? "변화 없음" : "이전 지출 없음";
  }
  if (item.amountChange === 0) {
    return `이전 ${amountFormatter.format(item.previousAmount)}원 · 변화 없음`;
  }

  const direction = item.amountChange > 0 ? "증가" : "감소";
  const rate = Math.abs(item.changeRatePercent ?? 0);
  return `이전 ${amountFormatter.format(item.previousAmount)}원 · ${amountFormatter.format(
    Math.abs(item.amountChange),
  )}원 ${direction} (${rate}%)`;
}

function periodLabel(statistics: SpendingStatistics): string {
  if (statistics.period === "DAY") {
    return dateFormatter.format(parseDate(statistics.startDate));
  }
  if (statistics.period === "MONTH") {
    return monthFormatter.format(parseDate(statistics.startDate));
  }
  const end = parseDate(statistics.endDateExclusive);
  end.setUTCDate(end.getUTCDate() - 1);
  return `${dateFormatter.format(parseDate(statistics.startDate))} – ${dateFormatter.format(end)}`;
}

function seoulToday(): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    day: "2-digit",
    month: "2-digit",
    timeZone: "Asia/Seoul",
    year: "numeric",
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

function parseDate(value: string): Date {
  return new Date(`${value}T00:00:00Z`);
}

function toDateInputValue(date: Date): string {
  return date.toISOString().slice(0, 10);
}
