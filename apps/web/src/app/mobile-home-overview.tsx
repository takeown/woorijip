"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import type { StoredValueAccount } from "./stored-value-account-panel";

type Props = {
  accounts: StoredValueAccount[];
  onOpenEntry: () => void;
};

type MonthlySummary = {
  current: {
    totalAmount: number;
  };
  amountChange: number;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");

export function MobileHomeOverview({ accounts, onOpenEntry }: Props) {
  const [summary, setSummary] = useState<MonthlySummary | null>(null);
  const [summaryError, setSummaryError] = useState(false);
  const activeAccounts = accounts.filter((account) => !account.archived);

  const fetchSummary = useCallback(async () => {
    setSummaryError(false);
    try {
      const response = await fetch(
        `${apiUrl}/statistics/spending?period=MONTH&payer=ALL&date=${seoulToday()}`,
        {
          cache: "no-store",
          credentials: "include",
        },
      );
      if (!response.ok) throw new Error();
      setSummary(await response.json());
    } catch {
      setSummaryError(true);
    }
  }, []);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const mediaQuery = window.matchMedia("(max-width: 1023px)");
    let requested = false;

    function loadForMobile() {
      if (!mediaQuery.matches || requested) return;
      requested = true;
      void fetchSummary();
    }

    loadForMobile();
    mediaQuery.addEventListener("change", loadForMobile);
    return () => mediaQuery.removeEventListener("change", loadForMobile);
  }, [fetchSummary]);

  return (
    <section className="lg:hidden">
      <p className="text-sm text-stone-500">우리 둘의 생활 기록</p>
      <h1 className="mt-1 text-2xl font-semibold tracking-tight">이번 달 우리집</h1>

      <div className="mt-5 rounded-3xl bg-emerald-800 p-5 text-white shadow-sm">
        <p className="text-sm text-emerald-100">이번 달에 지금까지 쓴 돈</p>
        {summary ? (
          <>
            <p className="mt-2 text-3xl font-semibold tracking-tight">
              {amountFormatter.format(summary.current.totalAmount)}원
            </p>
            <p className="mt-3 text-sm text-emerald-100">
              {summary.amountChange === 0
                ? "이전 기간과 지출이 같아요."
                : `이전 기간보다 ${amountFormatter.format(Math.abs(summary.amountChange))}원 ${summary.amountChange > 0 ? "많아요" : "적어요"}.`}
            </p>
          </>
        ) : summaryError ? (
          <p className="mt-3 text-sm text-emerald-100">지출 요약을 불러오지 못했습니다.</p>
        ) : (
          <p className="mt-3 text-sm text-emerald-100">지출을 계산하고 있습니다.</p>
        )}
      </div>

      <button
        className="mt-3 flex min-h-14 w-full items-center justify-between rounded-2xl border border-stone-200 bg-white px-4 text-left shadow-sm"
        onClick={onOpenEntry}
        type="button"
      >
        <span>
          <span className="block text-sm font-medium text-stone-900">빠르게 거래 기록하기</span>
          <span className="mt-1 block text-xs text-stone-500">예: 오늘 김밥 8천원</span>
        </span>
        <span className="rounded-xl bg-emerald-700 px-3 py-2 text-sm font-medium text-white">
          AI 입력
        </span>
      </button>

      <Link
        className="mt-3 block rounded-2xl border border-stone-200 bg-white p-4 shadow-sm"
        href="/balances"
      >
        <span className="flex items-center justify-between gap-3">
          <span>
            <span className="block text-sm font-medium text-stone-900">보유 잔액</span>
            <span className="mt-1 block text-xs text-stone-500">
              상품권·바우처·지역화폐 관리
            </span>
          </span>
          <span className="text-sm font-medium text-emerald-700">전체 보기</span>
        </span>
        {activeAccounts.length > 0 ? (
          <span className="mt-3 grid gap-2">
            {activeAccounts.slice(0, 2).map((account) => (
              <span className="flex items-center justify-between gap-3 text-sm" key={account.id}>
                <span className="min-w-0 truncate text-stone-600">
                  {account.ownerDisplayName} · {account.name}
                </span>
                <span className="shrink-0 font-semibold text-stone-900">
                  {amountFormatter.format(account.balance)}원
                </span>
              </span>
            ))}
            {activeAccounts.length > 2 ? (
              <span className="text-xs text-stone-500">외 {activeAccounts.length - 2}개</span>
            ) : null}
          </span>
        ) : (
          <span className="mt-3 block text-sm text-stone-500">사용 중인 잔액이 없습니다.</span>
        )}
      </Link>
    </section>
  );
}

function seoulToday(): string {
  return new Intl.DateTimeFormat("en-CA", {
    day: "2-digit",
    month: "2-digit",
    timeZone: "Asia/Seoul",
    year: "numeric",
  }).format(new Date());
}
