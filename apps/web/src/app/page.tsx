"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { AiTransactionDraftForm } from "./ai-transaction-draft-form";
import {
  AuthenticatedShell,
  type CurrentUser,
} from "./authenticated-shell";
import { MobileHomeOverview } from "./mobile-home-overview";
import { paymentDetailsLabel } from "./payment-details";
import { TransactionForm, type HouseholdMember } from "./transaction-form";
import {
  TransactionEditForm,
  type EditableTransaction,
} from "./transaction-edit-form";
import { categoryLabel, tagLabel } from "./transaction-classification";
import {
  StoredValueAccountPanel,
  type StoredValueAccount,
} from "./stored-value-account-panel";

type Transaction = EditableTransaction;

type PayerFilter = "all" | "me" | "partner";

type TransactionFilters = {
  payer: PayerFilter;
  query: string;
  from: string;
  to: string;
};

type TransactionPage = {
  items: Transaction[];
  nextCursor: string | null;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");
const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Seoul",
});
const initialFilters: TransactionFilters = {
  payer: "all",
  query: "",
  from: "",
  to: "",
};

export default function Home() {
  return (
    <AuthenticatedShell>
      {(currentUser) => <TransactionsPage currentUser={currentUser} />}
    </AuthenticatedShell>
  );
}

export function TransactionsPage({ currentUser }: { currentUser: CurrentUser }) {
  const [householdMembers, setHouseholdMembers] = useState<HouseholdMember[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [storedValueAccounts, setStoredValueAccounts] = useState<StoredValueAccount[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [filters, setFilters] = useState<TransactionFilters>(initialFilters);
  const [queryInput, setQueryInput] = useState("");
  const [fromInput, setFromInput] = useState("");
  const [toInput, setToInput] = useState("");
  const [editingTransactionId, setEditingTransactionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isChangingFilter, setIsChangingFilter] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isEntryPanelOpen, setIsEntryPanelOpen] = useState(false);
  const [summaryRefreshKey, setSummaryRefreshKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const pageRef = useRef<HTMLDivElement>(null);
  const entryPanelRef = useRef<HTMLElement>(null);
  const entryTriggerRef = useRef<HTMLElement | null>(null);

  const fetchTransactions = useCallback(async (
    nextFilters: TransactionFilters = initialFilters,
    cursor?: string,
  ) => {
    const searchParams = new URLSearchParams({ payer: nextFilters.payer });
    if (nextFilters.query) searchParams.set("q", nextFilters.query);
    if (nextFilters.from) searchParams.set("from", nextFilters.from);
    if (nextFilters.to) searchParams.set("to", nextFilters.to);
    if (cursor) searchParams.set("cursor", cursor);
    const response = await fetch(`${apiUrl}/transactions?${searchParams}`, {
      credentials: "include",
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error("거래 내역을 불러오지 못했습니다.");
    }
    return response.json() as Promise<TransactionPage>;
  }, []);

  const fetchHouseholdMembers = useCallback(async () => {
    const response = await fetch(`${apiUrl}/households/current/members`, {
      credentials: "include",
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error("가구 구성원을 불러오지 못했습니다.");
    }
    return response.json() as Promise<HouseholdMember[]>;
  }, []);

  const fetchStoredValueAccounts = useCallback(async () => {
    const response = await fetch(`${apiUrl}/stored-value-accounts`, {
      credentials: "include",
      cache: "no-store",
    });
    if (!response.ok) throw new Error("상품권·바우처 잔액을 불러오지 못했습니다.");
    return response.json() as Promise<StoredValueAccount[]>;
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([fetchTransactions(), fetchHouseholdMembers(), fetchStoredValueAccounts()])
      .then(([transactionPage, nextHouseholdMembers, nextStoredValueAccounts]) => {
        if (!active) return;
        setTransactions(transactionPage.items);
        setNextCursor(transactionPage.nextCursor);
        setHouseholdMembers(nextHouseholdMembers);
        setStoredValueAccounts(nextStoredValueAccounts);
      })
      .catch((caughtError: unknown) => {
        if (!active) return;
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : "거래 정보를 불러오지 못했습니다.",
        );
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [fetchHouseholdMembers, fetchStoredValueAccounts, fetchTransactions]);

  useEffect(() => {
    if (!isEntryPanelOpen) {
      entryTriggerRef.current?.focus();
      return;
    }
    const mediaQuery = window.matchMedia("(max-width: 1023px)");
    if (!mediaQuery.matches) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const entryPanel = entryPanelRef.current;
    const page = pageRef.current;
    const shell = page?.closest("main");
    const inertElements = [
      ...Array.from(shell?.children ?? []).filter((element) => element !== page),
      ...Array.from(page?.children ?? []).filter((element) => element !== entryPanel),
    ].filter((element): element is HTMLElement => element instanceof HTMLElement);
    const previousInertValues = inertElements.map((element) => element.inert);
    inertElements.forEach((element) => {
      element.inert = true;
    });

    if (entryPanel) {
      entryPanel.scrollTop = 0;
      entryPanel.focus();
    }

    function closePanel() {
      setIsEntryPanelOpen(false);
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === "Escape") closePanel();
    }

    function closeOnDesktop(event: MediaQueryListEvent) {
      if (!event.matches) closePanel();
    }

    window.addEventListener("keydown", closeOnEscape);
    mediaQuery.addEventListener("change", closeOnDesktop);
    return () => {
      document.body.style.overflow = previousOverflow;
      inertElements.forEach((element, index) => {
        element.inert = previousInertValues[index];
      });
      window.removeEventListener("keydown", closeOnEscape);
      mediaQuery.removeEventListener("change", closeOnDesktop);
    };
  }, [isEntryPanelOpen]);

  function openEntryPanel() {
    if (!window.matchMedia("(max-width: 1023px)").matches) return;
    entryTriggerRef.current = document.activeElement as HTMLElement | null;
    setIsEntryPanelOpen(true);
  }

  function closeEntryPanel() {
    setIsEntryPanelOpen(false);
  }

  async function replaceTransactions(nextFilters: TransactionFilters) {
    setError(null);
    setIsChangingFilter(true);
    try {
      const page = await fetchTransactions(nextFilters);
      setFilters(nextFilters);
      setTransactions(page.items);
      setNextCursor(page.nextCursor);
      setEditingTransactionId(null);
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : "거래 내역을 불러오지 못했습니다.",
      );
    } finally {
      setIsChangingFilter(false);
    }
  }

  async function changePayerFilter(payer: PayerFilter) {
    await replaceTransactions({ ...filters, payer });
  }

  async function searchTransactions(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = queryInput.trim();
    setQueryInput(query);
    await replaceTransactions({ ...filters, query });
  }

  async function applyDateFilter() {
    if (fromInput && toInput && fromInput > toInput) {
      setError("시작 날짜는 종료 날짜보다 늦을 수 없습니다.");
      return;
    }
    await replaceTransactions({ ...filters, from: fromInput, to: toInput });
  }

  async function applyMonthFilter(monthOffset: -1 | 0) {
    const range = monthRange(monthOffset);
    setFromInput(range.from);
    setToInput(range.to);
    await replaceTransactions({ ...filters, ...range });
  }

  async function clearFilters() {
    setQueryInput("");
    setFromInput("");
    setToInput("");
    await replaceTransactions(initialFilters);
  }

  async function handleTransactionCreated() {
    const [page, accounts] = await Promise.all([
      fetchTransactions(filters),
      fetchStoredValueAccounts(),
    ]);
    setTransactions(page.items);
    setNextCursor(page.nextCursor);
    setStoredValueAccounts(accounts);
    setSummaryRefreshKey((current) => current + 1);
    closeEntryPanel();
  }

  async function handleTransactionChanged() {
    const [page, accounts] = await Promise.all([
      fetchTransactions(filters),
      fetchStoredValueAccounts(),
    ]);
    setTransactions(page.items);
    setNextCursor(page.nextCursor);
    setStoredValueAccounts(accounts);
    setSummaryRefreshKey((current) => current + 1);
    setEditingTransactionId(null);
  }

  async function loadMoreTransactions() {
    if (!nextCursor || isLoadingMore || isChangingFilter) return;
    setError(null);
    setIsLoadingMore(true);
    try {
      const page = await fetchTransactions(filters, nextCursor);
      setTransactions((current) => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("거래 내역을 더 불러오지 못했습니다.");
    } finally {
      setIsLoadingMore(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-6xl rounded-3xl border border-stone-200 bg-white px-6 py-20 text-center text-stone-500 shadow-sm">
        거래 정보를 불러오고 있습니다.
      </div>
    );
  }

  return (
    <div
      className="mx-auto grid w-full max-w-6xl gap-5 lg:grid-cols-[380px_1fr] lg:gap-8"
      ref={pageRef}
    >
      <MobileHomeOverview
        accounts={storedValueAccounts}
        onOpenEntry={openEntryPanel}
        refreshKey={summaryRefreshKey}
      />

      <section
        aria-labelledby="transaction-entry-title"
        aria-modal={isEntryPanelOpen ? true : undefined}
        className={`${isEntryPanelOpen ? "fixed inset-0 z-40 block h-full w-full max-w-full overflow-x-hidden overflow-y-auto rounded-none border-0 bg-white p-0 shadow-none outline-none" : "hidden"} lg:static lg:col-start-1 lg:row-start-1 lg:block lg:h-fit lg:w-auto lg:max-w-none lg:overflow-visible lg:rounded-3xl lg:border lg:border-stone-200 lg:bg-white lg:p-7 lg:shadow-sm`}
        ref={entryPanelRef}
        role={isEntryPanelOpen ? "dialog" : undefined}
        tabIndex={isEntryPanelOpen ? -1 : undefined}
      >
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-stone-100 bg-white px-5 py-2 lg:hidden">
          <span className="font-semibold text-stone-900">거래 추가</span>
          <button
            className="min-h-11 min-w-11 rounded-xl text-2xl leading-none text-stone-500 hover:bg-stone-100"
            onClick={closeEntryPanel}
            type="button"
            aria-label="거래 추가 닫기"
          >
            ×
          </button>
        </div>
        <div className="min-w-0 px-5 pb-[calc(3rem+env(safe-area-inset-bottom))] pt-5 lg:p-0">
          <p className="text-sm font-medium text-emerald-700">우리 둘의 생활 기록</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight" id="transaction-entry-title">
            거래 입력
          </h1>
          <p className="mt-3 text-sm leading-6 text-stone-600">
            짧게 말하면 AI가 거래 초안을 만들어 드립니다.
          </p>
          <div className="mt-7">
            <AiTransactionDraftForm
              householdMembers={householdMembers}
              onCreated={handleTransactionCreated}
              storedValueAccounts={storedValueAccounts}
            />
          </div>
          <div className="hidden lg:block">
            <StoredValueAccountPanel
              accounts={storedValueAccounts}
              householdMembers={householdMembers}
              onChanged={async () => setStoredValueAccounts(await fetchStoredValueAccounts())}
            />
          </div>
          <Link
            className="mt-5 flex min-h-11 items-center justify-between rounded-xl bg-stone-50 px-4 text-sm text-stone-700 lg:hidden"
            href="/balances"
          >
            <span>상품권·바우처·지역화폐</span>
            <span className="font-medium text-emerald-700">잔액 관리</span>
          </Link>
          <div className="my-7 flex items-center gap-3 text-xs text-stone-400">
            <span className="h-px flex-1 bg-stone-200" />
            직접 입력
            <span className="h-px flex-1 bg-stone-200" />
          </div>
          <TransactionForm
            currentUserId={currentUser.id}
            householdMembers={householdMembers}
            storedValueAccounts={storedValueAccounts}
            onCreated={handleTransactionCreated}
          />
        </div>
      </section>

      <section
        aria-busy={isChangingFilter || isLoadingMore}
        className="border-t border-border-soft pt-6 lg:col-start-2 lg:row-start-1 lg:rounded-3xl lg:border lg:border-stone-200 lg:bg-white lg:p-7 lg:shadow-sm"
      >
        <div className="flex flex-wrap items-end justify-between gap-4">
          <h2 className="min-w-0 text-2xl font-semibold tracking-tight [overflow-wrap:anywhere] lg:text-3xl">거래 내역</h2>
          <p aria-live="polite" className="font-ui text-sm text-stone-500 tabular-nums">{transactions.length}건 표시</p>
        </div>

        <form
          aria-label="거래 검색"
          className="mt-5 flex items-end gap-2"
          onSubmit={searchTransactions}
        >
          <label className="min-w-0 flex-1 text-sm font-medium text-stone-700" htmlFor="transaction-query">
            가맹점·내역 검색
            <input
              className="mt-2 min-h-11 w-full min-w-0 rounded-xl border border-border-soft bg-surface px-4 font-ui text-base outline-2 outline-offset-2 outline-transparent transition-colors duration-150 ease-[var(--ease-out)] placeholder:text-stone-500 hover:bg-surface-muted focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isChangingFilter || isLoadingMore}
              id="transaction-query"
              maxLength={100}
              onChange={(event) => setQueryInput(event.target.value)}
              placeholder="예: 쿠팡, 기저귀"
              type="search"
              value={queryInput}
            />
          </label>
          <button
            className="min-h-11 shrink-0 whitespace-nowrap rounded-xl bg-accent px-5 py-2 font-medium text-accent-ink transition-[background-color,transform] duration-150 ease-[var(--ease-out)] hover:bg-accent-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:translate-y-px disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isChangingFilter || isLoadingMore}
            type="submit"
          >
            {isChangingFilter ? "조회 중…" : "검색"}
          </button>
        </form>

        <div className="mt-4 flex flex-wrap items-center gap-2" aria-label="결제자 필터">
          {([
            ["all", "전체"],
            ["me", "내 결제"],
            ["partner", "배우자"],
          ] as const).map(([filter, label]) => (
            <button
              aria-pressed={filters.payer === filter}
              className={`min-h-11 whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors duration-150 ease-[var(--ease-out)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:translate-y-px disabled:cursor-not-allowed disabled:opacity-50 ${
                filters.payer === filter
                  ? "bg-accent text-accent-ink"
                  : "bg-surface-muted text-stone-700 hover:bg-accent-soft"
              }`}
              key={filter}
              onClick={() => changePayerFilter(filter)}
              disabled={isChangingFilter || isLoadingMore}
              type="button"
            >
              {label}
            </button>
          ))}
          {hasActiveFilters(filters) ? (
            <button
              className="min-h-11 whitespace-nowrap px-2 text-sm font-medium text-accent-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isChangingFilter || isLoadingMore}
              onClick={() => void clearFilters()}
              type="button"
            >
              전체 초기화
            </button>
          ) : null}
        </div>

        <details className="mt-3 border-y border-border-soft py-1">
          <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 py-2 text-sm font-medium text-stone-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus [&::-webkit-details-marker]:hidden">
            <span>기간</span>
            <span className="flex min-w-0 items-center gap-2">
              <span className="min-w-0 truncate font-ui text-stone-500 tabular-nums">
                {dateFilterLabel(filters)}
              </span>
              <span className="shrink-0 text-accent-strong">선택</span>
            </span>
          </summary>
          <div className="grid gap-4 pb-4 pt-2">
            <div className="flex flex-wrap gap-2">
              <button
                className="min-h-11 whitespace-nowrap rounded-xl bg-surface-muted px-4 text-sm font-medium text-stone-700 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
                disabled={isChangingFilter || isLoadingMore}
                onClick={() => void applyMonthFilter(0)}
                type="button"
              >
                이번 달
              </button>
              <button
                className="min-h-11 whitespace-nowrap rounded-xl bg-surface-muted px-4 text-sm font-medium text-stone-700 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
                disabled={isChangingFilter || isLoadingMore}
                onClick={() => void applyMonthFilter(-1)}
                type="button"
              >
                지난달
              </button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="min-w-0 text-sm font-medium text-stone-700" htmlFor="transaction-from">
                시작 날짜
                <input
                  className="mt-2 min-h-11 w-full min-w-0 rounded-xl border border-border-soft bg-surface px-3 font-ui text-base tabular-nums outline-2 outline-offset-2 outline-transparent transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={isChangingFilter || isLoadingMore}
                  id="transaction-from"
                  onChange={(event) => setFromInput(event.target.value)}
                  type="date"
                  value={fromInput}
                />
              </label>
              <label className="min-w-0 text-sm font-medium text-stone-700" htmlFor="transaction-to">
                종료 날짜
                <input
                  className="mt-2 min-h-11 w-full min-w-0 rounded-xl border border-border-soft bg-surface px-3 font-ui text-base tabular-nums outline-2 outline-offset-2 outline-transparent transition-colors duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={isChangingFilter || isLoadingMore}
                  id="transaction-to"
                  onChange={(event) => setToInput(event.target.value)}
                  type="date"
                  value={toInput}
                />
              </label>
            </div>
            <button
              className="min-h-11 w-full whitespace-nowrap rounded-xl border border-border-soft bg-surface px-4 text-sm font-medium text-stone-700 transition-[background-color,transform] duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:translate-y-px disabled:cursor-not-allowed disabled:opacity-50 sm:w-fit"
              disabled={isChangingFilter || isLoadingMore}
              onClick={() => void applyDateFilter()}
              type="button"
            >
              기간 적용
            </button>
          </div>
        </details>

        {error ? (
          <p className="mt-8 rounded-2xl bg-amber-50 px-5 py-4 text-sm text-amber-800" role="alert">
            {error}
          </p>
        ) : null}

        {transactions.length === 0 && !error ? (
          <div className="mt-8 border-y border-border-soft py-12 text-left">
            <p className="font-medium text-stone-700">
              {hasActiveFilters(filters) ? "조건에 맞는 거래가 없습니다." : "아직 기록한 거래가 없습니다."}
            </p>
            <p className="mt-2 text-sm text-stone-500">
              {hasActiveFilters(filters) ? "검색어나 기간을 바꿔 보세요." : "거래 추가 버튼으로 첫 기록을 남겨 보세요."}
            </p>
          </div>
        ) : transactions.length > 0 ? (
          <>
            <ul className="mt-8 divide-y divide-stone-200">
              {transactions.map((transaction) => (
                <li className="flex items-start justify-between gap-3 py-5 lg:items-center lg:gap-5" key={transaction.id}>
                {editingTransactionId === transaction.id ? (
                  <TransactionEditForm
                    householdMembers={householdMembers}
                    storedValueAccounts={storedValueAccounts}
                    onCancel={() => setEditingTransactionId(null)}
                    onChanged={handleTransactionChanged}
                    transaction={transaction}
                  />
                ) : (
                  <>
                    <div className="min-w-0">
                      <p className="truncate font-medium text-stone-900">
                        {transaction.merchant}
                      </p>
                      {transaction.description ? (
                        <p className="mt-1 truncate text-sm text-stone-700">
                          {transaction.description}
                        </p>
                      ) : null}
                      <p className="mt-1 text-xs leading-5 text-stone-500 lg:text-sm">
                        {householdMembers.find(
                          (member) => member.userId === transaction.payerId,
                        )?.displayName ?? "알 수 없음"}{" "}
                        · {categoryLabel(transaction.category)} ·{" "}
                        {paymentDetailsLabel(
                          transaction.paymentMethod,
                          transaction.cardIssuer,
                        )}{" "}
                        · {dateFormatter.format(new Date(transaction.occurredAt))}
                      </p>
                      {transaction.tags.length > 0 ? (
                        <p className="mt-2 flex flex-wrap gap-1.5">
                          {transaction.tags.map((tag) => (
                            <span
                              className="rounded-full bg-stone-100 px-2 py-1 text-xs text-stone-600"
                              key={tag}
                            >
                              {tagLabel(tag)}
                            </span>
                          ))}
                        </p>
                      ) : null}
                      {transaction.storedValueAccountId ? (
                        <p className="mt-2 text-sm text-emerald-700">
                          {storedValueAccounts.find((account) => account.id === transaction.storedValueAccountId)?.name ?? "별도 잔액"} 사용
                        </p>
                      ) : null}
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="font-ui font-semibold text-stone-900 tabular-nums">
                        {amountFormatter.format(transaction.amount)}원
                      </p>
                      <button
                        className="mt-1 min-h-11 px-2 text-sm font-medium text-emerald-700 hover:text-emerald-800 lg:mt-2 lg:min-h-0 lg:px-0"
                        onClick={() => setEditingTransactionId(transaction.id)}
                        type="button"
                      >
                        수정
                      </button>
                    </div>
                  </>
                )}
                </li>
              ))}
            </ul>
            {nextCursor ? (
              <button
                className="mt-6 w-full whitespace-nowrap rounded-xl border border-border-soft bg-surface px-4 py-3 text-sm font-medium text-stone-700 transition-[background-color,transform] duration-150 ease-[var(--ease-out)] hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:translate-y-px disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isLoadingMore || isChangingFilter}
                onClick={loadMoreTransactions}
                type="button"
              >
                {isLoadingMore ? "불러오는 중…" : "더 보기"}
              </button>
            ) : null}
          </>
        ) : null}
      </section>
      <button
        className="fixed bottom-[calc(5.25rem+env(safe-area-inset-bottom))] right-5 z-20 flex h-14 w-14 items-center justify-center rounded-full bg-accent text-3xl font-light text-accent-ink shadow-lg transition-[background-color,transform] duration-150 ease-[var(--ease-out)] hover:bg-accent-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:translate-y-px lg:hidden"
        onClick={openEntryPanel}
        type="button"
        aria-label="거래 추가"
      >
        +
      </button>
    </div>
  );
}

function hasActiveFilters(filters: TransactionFilters): boolean {
  return filters.payer !== "all" || Boolean(filters.query || filters.from || filters.to);
}

function dateFilterLabel(filters: TransactionFilters): string {
  if (!filters.from && !filters.to) return "전체 기간";
  if (filters.from && filters.to) return `${filters.from} – ${filters.to}`;
  if (filters.from) return `${filters.from}부터`;
  return `${filters.to}까지`;
}

function monthRange(offset: -1 | 0): Pick<TransactionFilters, "from" | "to"> {
  const [year, month] = seoulToday().split("-").map(Number);
  const firstDay = new Date(Date.UTC(year, month - 1 + offset, 1));
  const lastDay = new Date(Date.UTC(year, month + offset, 0));
  return {
    from: firstDay.toISOString().slice(0, 10),
    to: lastDay.toISOString().slice(0, 10),
  };
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
