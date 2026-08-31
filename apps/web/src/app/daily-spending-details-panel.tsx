/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V4 */
/* Hallmark · genre: modern-minimal · macrostructure: Workbench
 * theme: Woorijip custom · tone: soft + utilitarian · designed-as-app
 */
"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AuthenticatedShell } from "./authenticated-shell";
import {
  calendarReturnUrl,
  dailyStatsUrl,
  type SpendingPayer,
} from "./stats-url-state";

type DailySpendingTransaction = {
  id: number;
  merchant: string;
  description: string | null;
  amount: number;
  occurredAt: string;
  payerLabel: string;
  paymentMethodLabel: string;
  categoryLabel: string;
  tagLabels: string[];
};

type DailySpendingStatistics = {
  current: {
    totalAmount: number;
    coupleLivingAmount: number;
    childcareAmount: number;
    transactionCount: number;
  };
  dailyTransactions?: DailySpendingTransaction[];
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");
const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "full",
  timeZone: "UTC",
});
const transactionTimeFormatter = new Intl.DateTimeFormat("ko-KR", {
  hour: "2-digit",
  hour12: false,
  minute: "2-digit",
  timeZone: "Asia/Seoul",
});

export function DailySpendingDetailsPage({
  date,
  payer,
  statsDate,
}: {
  date: string;
  payer: SpendingPayer;
  statsDate: string;
}) {
  return (
    <AuthenticatedShell>
      {() => <DailySpendingDetailsPanel date={date} payer={payer} statsDate={statsDate} />}
    </AuthenticatedShell>
  );
}

export function DailySpendingDetailsPanel({
  date,
  payer,
  statsDate,
}: {
  date: string;
  payer: SpendingPayer;
  statsDate: string;
}) {
  const requestKey = `${date}:${payer}`;
  const [loadResult, setLoadResult] = useState<{
    requestKey: string;
    statistics: DailySpendingStatistics | null;
    error: string | null;
  }>({ requestKey: "", statistics: null, error: null });

  useEffect(() => {
    const controller = new AbortController();

    fetch(
      `${apiUrl}/statistics/spending?period=DAY&payer=${payer}&date=${date}&includeDailyTransactions=true`,
      {
        cache: "no-store",
        credentials: "include",
        signal: controller.signal,
      },
    )
      .then(async (response) => {
        if (!response.ok) throw new Error("이날 지출을 불러오지 못했습니다.");
        setLoadResult({ requestKey, statistics: await response.json(), error: null });
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        setLoadResult({
          requestKey,
          statistics: null,
          error:
            caughtError instanceof Error
              ? caughtError.message
              : "이날 지출을 불러오지 못했습니다.",
        });
      });

    return () => controller.abort();
  }, [date, payer, requestKey]);

  const previousDate = moveDate(date, -1);
  const nextDate = moveDate(date, 1);
  const statistics = loadResult.requestKey === requestKey ? loadResult.statistics : null;
  const error = loadResult.requestKey === requestKey ? loadResult.error : null;

  return (
    <section className="mx-auto mb-8 w-full max-w-6xl">
      <Link
        className="inline-flex min-h-11 items-center whitespace-nowrap text-sm font-medium text-accent-strong transition-colors duration-150 ease-[var(--ease-out)] hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:text-accent"
        href={calendarReturnUrl(payer, statsDate)}
      >
        ← 소비 캘린더로 돌아가기
      </Link>

      <header className="mt-4 flex flex-col gap-4 border-b border-border-soft pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h2 className="text-3xl font-semibold tracking-tight [overflow-wrap:anywhere]">
            {dateFormatter.format(parseDate(date))} 지출
          </h2>
          <p className="mt-2 text-sm text-stone-600">{payerLabel(payer)} 결제 내역입니다.</p>
        </div>
        <nav className="flex shrink-0 gap-2" aria-label="일간 통계 날짜 이동">
          <Link
            aria-label="이전 날짜"
            className="flex min-h-11 min-w-11 items-center justify-center rounded-xl border border-border-soft bg-surface text-stone-700 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:bg-surface-muted"
            href={dailyStatsUrl(previousDate, payer, statsDate)}
          >
            ←
          </Link>
          <Link
            aria-label="다음 날짜"
            className="flex min-h-11 min-w-11 items-center justify-center rounded-xl border border-border-soft bg-surface text-stone-700 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-accent-ink active:bg-surface-muted"
            href={dailyStatsUrl(nextDate, payer, statsDate)}
          >
            →
          </Link>
        </nav>
      </header>

      {error ? (
        <p className="mt-8 rounded-xl bg-red-50 px-5 py-4 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : statistics === null ? (
        <div className="mt-8 border-y border-border-soft bg-surface-muted px-5 py-12 text-center text-stone-600" role="status">
          이날 거래를 불러오고 있습니다.
        </div>
      ) : (
        <>
          <DailySummary statistics={statistics} />
          <DailyTransactionDetails transactions={statistics.dailyTransactions ?? []} />
        </>
      )}
    </section>
  );
}

function DailySummary({ statistics }: { statistics: DailySpendingStatistics }) {
  return (
    <dl className="mt-8 grid border-y border-border-soft sm:grid-cols-2 lg:grid-cols-4">
      <DailySummaryFact label="총지출" value={`${amountFormatter.format(statistics.current.totalAmount)}원`} />
      <DailySummaryFact label="거래 건수" value={`${amountFormatter.format(statistics.current.transactionCount)}건`} />
      <DailySummaryFact label="부부 생활비" value={`${amountFormatter.format(statistics.current.coupleLivingAmount)}원`} />
      <DailySummaryFact label="육아비" value={`${amountFormatter.format(statistics.current.childcareAmount)}원`} />
    </dl>
  );
}

function DailySummaryFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="border-b border-border-soft px-4 py-4 last:border-b-0 sm:[&:nth-last-child(-n+2)]:border-b-0 lg:border-b-0 lg:border-r lg:last:border-r-0">
      <dt className="text-xs text-stone-600">{label}</dt>
      <dd className="mt-1 font-ui text-lg font-semibold text-stone-900 tabular-nums">{value}</dd>
    </div>
  );
}

function DailyTransactionDetails({ transactions }: { transactions: DailySpendingTransaction[] }) {
  return (
    <section aria-labelledby="daily-transaction-details-title" className="mt-8 border-y border-border-soft">
      <header className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-4">
        <h3 className="text-xl font-semibold text-foreground" id="daily-transaction-details-title">
          이날 거래 내역
        </h3>
        <p className="font-ui text-sm text-stone-600 tabular-nums">
          {amountFormatter.format(transactions.length)}건 모두 표시
        </p>
      </header>

      {transactions.length === 0 ? (
        <p className="border-t border-border-soft py-8 text-sm text-stone-600">
          이날에는 기록된 거래가 없습니다.
        </p>
      ) : (
        <>
          <div className="hidden overflow-x-auto lg:block">
            <table className="w-full min-w-[48rem] border-collapse text-left text-sm">
              <caption className="sr-only">선택한 날짜의 전체 거래 내역</caption>
              <thead>
                <tr className="border-y border-border-soft text-stone-600">
                  <th className="whitespace-nowrap px-3 py-3 font-medium" scope="col">시간</th>
                  <th className="px-3 py-3 font-medium" scope="col">가맹점·내역</th>
                  <th className="whitespace-nowrap px-3 py-3 font-medium" scope="col">결제자</th>
                  <th className="whitespace-nowrap px-3 py-3 font-medium" scope="col">결제수단</th>
                  <th className="px-3 py-3 font-medium" scope="col">분류</th>
                  <th className="whitespace-nowrap px-3 py-3 text-right font-medium" scope="col">금액</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-soft">
                {transactions.map((transaction) => (
                  <tr key={transaction.id}>
                    <td className="whitespace-nowrap px-3 py-4 align-top font-ui text-stone-600 tabular-nums">{transactionTimeFormatter.format(new Date(transaction.occurredAt))}</td>
                    <td className="min-w-48 px-3 py-4 align-top">
                      <p className="font-medium text-stone-900">{transaction.merchant}</p>
                      {transaction.description ? <p className="mt-1 text-stone-600">{transaction.description}</p> : null}
                    </td>
                    <td className="whitespace-nowrap px-3 py-4 align-top text-stone-700">{transaction.payerLabel}</td>
                    <td className="whitespace-nowrap px-3 py-4 align-top text-stone-700">{transaction.paymentMethodLabel}</td>
                    <td className="px-3 py-4 align-top text-stone-700">
                      <p>{transaction.categoryLabel}</p>
                      {transaction.tagLabels.length > 0 ? <p className="mt-1 text-xs text-stone-600">{transaction.tagLabels.join(" · ")}</p> : null}
                    </td>
                    <td className="whitespace-nowrap px-3 py-4 text-right align-top font-ui font-semibold text-stone-900 tabular-nums">{amountFormatter.format(transaction.amount)}원</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <ul className="divide-y divide-border-soft border-t border-border-soft lg:hidden">
            {transactions.map((transaction) => (
              <li className="py-4" key={transaction.id}>
                <div className="flex min-w-0 items-baseline justify-between gap-4">
                  <p className="min-w-0 truncate font-medium text-stone-900">{transaction.merchant}</p>
                  <p className="shrink-0 font-ui font-semibold text-stone-900 tabular-nums">{amountFormatter.format(transaction.amount)}원</p>
                </div>
                {transaction.description ? <p className="mt-1 text-sm text-stone-700 [overflow-wrap:anywhere]">{transaction.description}</p> : null}
                <p className="mt-2 text-xs leading-5 text-stone-600">
                  <span className="font-ui tabular-nums">{transactionTimeFormatter.format(new Date(transaction.occurredAt))}</span>{" "}
                  · {transaction.payerLabel} · {transaction.paymentMethodLabel} · {transaction.categoryLabel}
                  {transaction.tagLabels.length > 0 ? ` · ${transaction.tagLabels.join(" · ")}` : ""}
                </p>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

function moveDate(value: string, days: number): string {
  const date = parseDate(value);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function parseDate(value: string): Date {
  return new Date(`${value}T00:00:00Z`);
}

function payerLabel(payer: SpendingPayer): string {
  if (payer === "ME") return "내";
  if (payer === "PARTNER") return "배우자";
  return "전체 결제자";
}
