"use client";

import { useEffect, useState } from "react";

type SpendingPeriod = "DAY" | "WEEK" | "MONTH";
type SpendingPayer = "ALL" | "ME" | "PARTNER";

type PeriodSummary = {
  totalAmount: number;
  transactionCount: number;
};

type BreakdownItem = {
  key: string;
  label: string;
  amount: number;
  transactionCount: number;
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

export function SpendingStatisticsPanel({ refreshKey }: SpendingStatisticsPanelProps) {
  const [period, setPeriod] = useState<SpendingPeriod>("MONTH");
  const [payer, setPayer] = useState<SpendingPayer>("ALL");
  const [referenceDate, setReferenceDate] = useState(seoulToday);
  const requestKey = `${period}:${payer}:${referenceDate}:${refreshKey}`;
  const requestUrl = `${apiUrl}/statistics/spending?period=${period}&payer=${payer}&date=${referenceDate}`;
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
    <section className="mx-auto mb-8 w-full max-w-6xl rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-5">
        <div>
          <p className="text-sm font-medium text-emerald-700">우리집 지출 흐름</p>
          <h2 className="mt-2 text-3xl font-semibold tracking-tight">기간별 통계</h2>
          <p className="mt-2 text-sm text-stone-500">
            서울 시간 기준으로 기록된 지출을 비교합니다.
          </p>
        </div>
        <div className="flex rounded-full bg-stone-100 p-1" aria-label="통계 기간">
          {([
            ["DAY", "일간"],
            ["WEEK", "주간"],
            ["MONTH", "월간"],
          ] as const).map(([value, label]) => (
            <button
              className={`rounded-full px-4 py-2 text-sm font-medium transition ${
                period === value
                  ? "bg-emerald-700 text-white"
                  : "text-stone-600 hover:bg-stone-200"
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

      <div className="mt-5 flex flex-wrap items-center gap-3">
        <p className="text-sm font-medium text-stone-600">누구의 지출</p>
        <div className="flex rounded-full bg-stone-100 p-1" aria-label="통계 결제자">
          {([
            ["ALL", "전체"],
            ["ME", "나"],
            ["PARTNER", "배우자"],
          ] as const).map(([value, label]) => (
            <button
              className={`rounded-full px-4 py-2 text-sm font-medium transition ${
                payer === value
                  ? "bg-stone-800 text-white"
                  : "text-stone-600 hover:bg-stone-200"
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

      <div className="mt-6 flex flex-wrap items-center gap-2">
        <button
          aria-label="이전 기간"
          className="rounded-xl border border-stone-300 bg-white px-3 py-2 text-stone-600 hover:bg-stone-50"
          onClick={() => movePeriod(-1)}
          type="button"
        >
          ←
        </button>
        <input
          aria-label="통계 기준 날짜"
          className="rounded-xl border border-stone-300 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          onChange={(event) => setReferenceDate(event.target.value)}
          type="date"
          value={referenceDate}
        />
        <button
          aria-label="다음 기간"
          className="rounded-xl border border-stone-300 bg-white px-3 py-2 text-stone-600 hover:bg-stone-50"
          onClick={() => movePeriod(1)}
          type="button"
        >
          →
        </button>
        <button
          className="rounded-xl px-3 py-2 text-sm font-medium text-emerald-700 hover:bg-emerald-50"
          onClick={() => setReferenceDate(seoulToday())}
          type="button"
        >
          오늘
        </button>
        {statistics ? (
          <p className="ml-auto text-sm text-stone-500">{periodLabel(statistics)}</p>
        ) : null}
      </div>

      {isLoading ? (
        <div className="mt-8 rounded-2xl bg-stone-50 px-5 py-12 text-center text-stone-500">
          통계를 계산하고 있습니다.
        </div>
      ) : error ? (
        <p className="mt-8 rounded-2xl bg-red-50 px-5 py-4 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : statistics ? (
        <>
          <div className="mt-8 grid gap-4 sm:grid-cols-3">
            <SummaryCard
              label="총지출"
              value={`${amountFormatter.format(statistics.current.totalAmount)}원`}
            />
            <SummaryCard
              label="거래 건수"
              value={`${amountFormatter.format(statistics.current.transactionCount)}건`}
            />
            <SummaryCard
              label="이전 기간 대비"
              tone={statistics.amountChange > 0 ? "warning" : "normal"}
              value={comparisonLabel(statistics)}
            />
          </div>

          {statistics.current.transactionCount === 0 ? (
            <div className="mt-6 rounded-2xl border border-dashed border-stone-300 px-6 py-12 text-center">
              <p className="font-medium text-stone-700">이 기간에는 기록된 지출이 없습니다.</p>
              <p className="mt-2 text-sm text-stone-500">
                다른 날짜나 기간을 선택해 보세요.
              </p>
            </div>
          ) : (
            <div
              className={`mt-6 grid gap-4 ${
                statistics.payer === "ALL" ? "lg:grid-cols-3" : "lg:grid-cols-2"
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
              <Breakdown
                title="카테고리"
                items={statistics.byCategory}
                total={statistics.current.totalAmount}
              />
            </div>
          )}
        </>
      ) : null}
    </section>
  );
}

function SummaryCard({
  label,
  value,
  tone = "normal",
}: {
  label: string;
  value: string;
  tone?: "normal" | "warning";
}) {
  return (
    <div className="rounded-2xl bg-stone-50 p-5">
      <p className="text-sm text-stone-500">{label}</p>
      <p
        className={`mt-2 text-2xl font-semibold ${
          tone === "warning" ? "text-amber-700" : "text-stone-900"
        }`}
      >
        {value}
      </p>
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
    <div className="rounded-2xl border border-stone-200 p-5">
      <h3 className="font-semibold text-stone-900">{title}</h3>
      <ul className="mt-4 space-y-4">
        {items.map((item) => (
          <li key={item.key}>
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="truncate text-stone-700">{item.label}</span>
              <span className="shrink-0 font-medium text-stone-900">
                {amountFormatter.format(item.amount)}원
              </span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-stone-100">
              <div
                className="h-full rounded-full bg-emerald-600"
                style={{ width: `${total === 0 ? 0 : (item.amount / total) * 100}%` }}
              />
            </div>
            <p className="mt-1 text-xs text-stone-400">{item.transactionCount}건</p>
          </li>
        ))}
      </ul>
    </div>
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
