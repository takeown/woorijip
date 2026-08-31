/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V5 */
/* Hallmark · genre: modern-minimal · macrostructure: Workbench
 * theme: Woorijip custom · tone: soft + utilitarian · designed-as-app
 */
"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import {
  dailyStatsUrl,
  statsUrl,
  todayInSeoul,
  type SpendingPayer,
  type SpendingPeriod,
  type StatsUrlState,
} from "./stats-url-state";

type PeriodSummary = {
  totalAmount: number;
  coupleLivingAmount: number;
  childcareAmount: number;
  transactionCount: number;
};

type DailySpendingBreakdown = {
  date: string;
  totalAmount: number;
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
  dailyBreakdown?: DailySpendingBreakdown[];
};

type SpendingStatisticsPanelProps = {
  initialState?: StatsUrlState;
  refreshKey: number;
};

type SpendingAnalysisAnswer = {
  status: "ANSWERED" | "NO_DATA" | "UNSUPPORTED";
  answer: string;
  evidenceTransactions: SpendingEvidenceTransaction[];
  dataLimited: boolean;
  remainingRequestsToday: number;
};

type CsrfToken = {
  token: string;
  headerName: string;
};

type ApiProblem = {
  detail?: unknown;
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
const calendarDateFormatter = new Intl.DateTimeFormat("ko-KR", {
  day: "numeric",
  month: "long",
  timeZone: "UTC",
});
const compactAmountFormatter = new Intl.NumberFormat("ko-KR", {
  maximumFractionDigits: 0,
  notation: "compact",
});
const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"] as const;

export function SpendingStatisticsPanel({ initialState, refreshKey }: SpendingStatisticsPanelProps) {
  const [viewState, setViewState] = useState<StatsUrlState>(() =>
    initialState ?? {
      calendarExpanded: false,
      payer: "ALL",
      period: "MONTH",
      referenceDate: todayInSeoul(),
    },
  );
  const {
    calendarExpanded: isCalendarExpanded,
    payer,
    period,
    referenceDate,
  } = viewState;
  const requestKey = `${period}:${payer}:${referenceDate}:${refreshKey}`;
  const requestUrl = `${apiUrl}/statistics/spending?period=${period}&payer=${payer}&date=${referenceDate}&includeMonthlySummary=true&includeDailyBreakdown=true`;
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

  function updateViewState(nextState: StatsUrlState) {
    setViewState(nextState);
    window.history.replaceState(null, "", statsUrl(nextState));
  }

  function movePeriod(direction: -1 | 1) {
    const date = parseDate(referenceDate);
    if (period === "DAY") date.setUTCDate(date.getUTCDate() + direction);
    if (period === "WEEK") date.setUTCDate(date.getUTCDate() + direction * 7);
    if (period === "MONTH") {
      date.setUTCDate(1);
      date.setUTCMonth(date.getUTCMonth() + direction);
    }
    updateViewState({ ...viewState, referenceDate: toDateInputValue(date) });
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
                  onClick={() => updateViewState({ ...viewState, period: value })}
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
                  onClick={() => updateViewState({ ...viewState, payer: value })}
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
                onChange={(event) =>
                  updateViewState({ ...viewState, referenceDate: event.target.value })
                }
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
                onClick={() =>
                  updateViewState({ ...viewState, referenceDate: todayInSeoul() })
                }
                type="button"
              >
                오늘
              </button>
            </div>
          </div>
        </div>
      </div>

      <SpendingQuestion />

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

          {statistics.period === "MONTH" && statistics.dailyBreakdown ? (
            <MonthlySpendingCalendar
              dailyBreakdown={statistics.dailyBreakdown}
              endDateExclusive={statistics.endDateExclusive}
              isExpanded={isCalendarExpanded}
              onToggle={() =>
                updateViewState({
                  ...viewState,
                  calendarExpanded: !isCalendarExpanded,
                })
              }
              payer={payer}
              statsDate={referenceDate}
              startDate={statistics.startDate}
            />
          ) : null}

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

function MonthlySpendingCalendar({
  dailyBreakdown,
  endDateExclusive,
  isExpanded,
  onToggle,
  payer,
  statsDate,
  startDate,
}: {
  dailyBreakdown: DailySpendingBreakdown[];
  endDateExclusive: string;
  isExpanded: boolean;
  onToggle: () => void;
  payer: SpendingPayer;
  statsDate: string;
  startDate: string;
}) {
  const spendingByDate = new Map(dailyBreakdown.map((item) => [item.date, item]));
  const weeks = calendarWeeks(startDate, endDateExclusive);
  const highestSpending = dailyBreakdown.reduce<DailySpendingBreakdown | null>(
    (highest, item) =>
      highest === null || item.totalAmount > highest.totalAmount ? item : highest,
    null,
  );

  return (
    <section aria-labelledby="monthly-spending-calendar-title" className="mt-8">
      <div className="flex flex-col gap-4 border-y border-border-soft py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <h3 className="text-xl font-semibold text-foreground" id="monthly-spending-calendar-title">
            날짜별 지출
          </h3>
          <p className="mt-1 text-sm text-stone-600">
            {highestSpending ? (
              <>
                가장 많이 쓴 날 {calendarDateFormatter.format(parseDate(highestSpending.date))} ·{" "}
                <span className="font-ui tabular-nums">
                  {amountFormatter.format(highestSpending.totalAmount)}원
                </span>
              </>
            ) : (
              "이달에는 기록된 지출이 없습니다."
            )}
          </p>
        </div>
        <button
          aria-controls="monthly-spending-calendar"
          aria-expanded={isExpanded}
          className="min-h-11 shrink-0 self-start whitespace-nowrap rounded-xl border border-border-soft bg-surface px-4 py-2 text-sm font-medium text-accent-strong transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:bg-accent-soft sm:self-auto"
          onClick={onToggle}
          type="button"
        >
          {isExpanded ? "달력 접기" : "달력 펼치기"}
        </button>
      </div>

      {isExpanded ? (
        <div className="overflow-hidden" id="monthly-spending-calendar">
          <p className="py-3 text-sm text-stone-600">
            날짜를 누르면 그날의 통계로 이동합니다.
          </p>
          <table className="w-full table-fixed border-collapse border-t border-border-soft">
            <caption className="sr-only">
              {monthFormatter.format(parseDate(startDate))} 날짜별 지출 달력
            </caption>
            <thead>
              <tr>
                {WEEKDAYS.map((weekday) => (
                  <th
                    className="border-b border-border-soft py-2 font-ui text-xs font-medium text-stone-600"
                    key={weekday}
                    scope="col"
                  >
                    {weekday}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {weeks.map((week, weekIndex) => (
                <tr key={weekIndex}>
                  {week.map((date, dayIndex) => {
                    if (date === null) {
                      return (
                        <td
                          aria-hidden="true"
                          className="h-16 border-b border-r border-border-soft bg-surface-muted last:border-r-0 sm:h-20"
                          key={`empty-${dayIndex}`}
                        />
                      );
                    }
                    const spending = spendingByDate.get(date);
                    const label = spending
                      ? `${calendarDateFormatter.format(parseDate(date))}, ${amountFormatter.format(spending.totalAmount)}원, ${amountFormatter.format(spending.transactionCount)}건`
                      : `${calendarDateFormatter.format(parseDate(date))}, 지출 없음`;

                    return (
                      <td
                        className="border-b border-r border-border-soft p-0 last:border-r-0"
                        key={date}
                      >
                        <Link
                          aria-label={label}
                          className="flex min-h-16 w-full min-w-0 flex-col items-start justify-between px-1 py-2 text-left transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-soft focus-visible:relative focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus active:bg-accent-soft sm:min-h-20 sm:px-2"
                          href={dailyStatsUrl(date, payer, statsDate)}
                        >
                          <span className="font-ui text-xs font-medium text-stone-700 tabular-nums">
                            {Number(date.slice(-2))}
                          </span>
                          <span
                            className={`min-w-0 max-w-full truncate font-ui text-xs font-semibold tabular-nums ${
                              spending ? "text-stone-900" : "text-stone-500"
                            }`}
                          >
                            {spending
                              ? `${compactAmountFormatter.format(spending.totalAmount)}원`
                              : "—"}
                          </span>
                        </Link>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

function SpendingQuestion() {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<SpendingAnalysisAnswer | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = question.trim();
    if (!value || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);
    setAnswer(null);
    try {
      const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, {
        credentials: "include",
      });
      if (!csrfResponse.ok) {
        throw new Error("질문을 준비하지 못했습니다.");
      }
      const csrf: CsrfToken = await csrfResponse.json();
      const response = await fetch(`${apiUrl}/statistics/spending-answers`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({ question: value }),
      });
      if (!response.ok) {
        const problem: ApiProblem | null = await response.json().catch(() => null);
        throw new Error(
          typeof problem?.detail === "string"
            ? problem.detail
            : "가계 분석 답변을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.",
        );
      }
      setAnswer(await response.json());
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : "가계 분석 답변을 만들지 못했습니다.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section
      aria-labelledby="spending-question-title"
      className="mt-8 border-y border-border-soft bg-surface-muted px-4 py-6 sm:px-5"
    >
      <div className="max-w-2xl">
        <h3 className="text-xl font-semibold text-foreground" id="spending-question-title">
          우리집 지출에 물어보기
        </h3>
        <p className="mt-2 text-sm text-stone-600">
          저장된 최근 거래내역 안에서 답하고, 확인에 쓴 거래를 함께 보여드려요.
        </p>
      </div>

      <form className="mt-5 flex flex-col gap-3 sm:flex-row" onSubmit={submit}>
        <label className="sr-only" htmlFor="spending-question">
          가계 지출 질문
        </label>
        <input
          className="min-h-11 min-w-0 flex-1 rounded-xl border border-border-soft bg-surface px-4 text-base outline-2 outline-offset-2 outline-transparent transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-soft focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-60"
          disabled={isSubmitting}
          id="spending-question"
          maxLength={200}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder="예: 지난달보다 식비가 왜 늘었어?"
          value={question}
        />
        <button
          className="min-h-11 whitespace-nowrap rounded-xl bg-accent px-5 py-2.5 font-semibold text-accent-ink transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50"
          disabled={isSubmitting || question.trim().length === 0}
          type="submit"
        >
          {isSubmitting ? "살펴보는 중" : "물어보기"}
        </button>
      </form>

      {error ? (
        <p className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : null}

      {answer ? (
        <div className="mt-5 border-t border-border-soft pt-5" aria-live="polite">
          <p className="text-base leading-7 text-stone-800">{answer.answer}</p>
          {answer.dataLimited ? (
            <p className="mt-2 text-xs text-stone-600">
              최근 거래 일부와 외부 전송에 안전한 거래만 기준으로 살펴봤어요.
            </p>
          ) : null}
          {answer.evidenceTransactions.length > 0 ? (
            <div className="mt-5">
              <h4 className="text-sm font-semibold text-stone-900">이 답변의 근거</h4>
              <ul className="mt-2 divide-y divide-border-soft">
                {answer.evidenceTransactions.map((transaction) => (
                  <li
                    className="flex items-baseline justify-between gap-4 py-3"
                    key={transaction.id}
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-stone-800">
                        {transaction.merchant}
                      </p>
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
          ) : null}
          <p className="mt-3 font-ui text-xs text-stone-600 tabular-nums">
            오늘 {amountFormatter.format(answer.remainingRequestsToday)}번 더 물어볼 수 있어요.
          </p>
        </div>
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
  if (startDate.slice(0, 7) === todayInSeoul().slice(0, 7)) {
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

function parseDate(value: string): Date {
  return new Date(`${value}T00:00:00Z`);
}

function toDateInputValue(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function calendarWeeks(startDate: string, endDateExclusive: string): Array<Array<string | null>> {
  const start = parseDate(startDate);
  const end = parseDate(endDateExclusive);
  const dates: Array<string | null> = Array((start.getUTCDay() + 6) % 7).fill(null);

  for (const current = new Date(start); current < end; current.setUTCDate(current.getUTCDate() + 1)) {
    dates.push(toDateInputValue(current));
  }
  while (dates.length % 7 !== 0) dates.push(null);

  return Array.from({ length: dates.length / 7 }, (_, index) =>
    dates.slice(index * 7, index * 7 + 7),
  );
}
