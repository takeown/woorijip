"use client";

import { useCallback, useEffect, useState } from "react";
import { AiTransactionDraftForm } from "./ai-transaction-draft-form";
import {
  AuthenticatedShell,
  type CurrentUser,
} from "./authenticated-shell";
import { paymentDetailsLabel } from "./payment-details";
import { TransactionForm, type HouseholdMember } from "./transaction-form";
import {
  TransactionEditForm,
  type EditableTransaction,
} from "./transaction-edit-form";
import { categoryLabel, tagLabel } from "./transaction-classification";

type Transaction = EditableTransaction;

type PayerFilter = "all" | "me" | "partner";

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
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [payerFilter, setPayerFilter] = useState<PayerFilter>("all");
  const [editingTransactionId, setEditingTransactionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isChangingFilter, setIsChangingFilter] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchTransactions = useCallback(async (
    filter: PayerFilter = "all",
    cursor?: string,
  ) => {
    const searchParams = new URLSearchParams({ payer: filter });
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

  useEffect(() => {
    let active = true;
    Promise.all([fetchTransactions(), fetchHouseholdMembers()])
      .then(([transactionPage, nextHouseholdMembers]) => {
        if (!active) return;
        setTransactions(transactionPage.items);
        setNextCursor(transactionPage.nextCursor);
        setHouseholdMembers(nextHouseholdMembers);
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
  }, [fetchHouseholdMembers, fetchTransactions]);

  async function changePayerFilter(filter: PayerFilter) {
    setError(null);
    setIsChangingFilter(true);
    try {
      const page = await fetchTransactions(filter);
      setPayerFilter(filter);
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

  async function handleTransactionCreated() {
    const page = await fetchTransactions(payerFilter);
    setTransactions(page.items);
    setNextCursor(page.nextCursor);
  }

  async function handleTransactionChanged() {
    const page = await fetchTransactions(payerFilter);
    setTransactions(page.items);
    setNextCursor(page.nextCursor);
    setEditingTransactionId(null);
  }

  async function loadMoreTransactions() {
    if (!nextCursor || isLoadingMore || isChangingFilter) return;
    setError(null);
    setIsLoadingMore(true);
    try {
      const page = await fetchTransactions(payerFilter, nextCursor);
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
    <div className="mx-auto grid w-full max-w-6xl gap-8 lg:grid-cols-[380px_1fr]">
      <section className="h-fit rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
        <p className="text-sm font-medium text-emerald-700">우리 둘의 생활 기록</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">거래 입력</h1>
        <p className="mt-3 text-sm leading-6 text-stone-600">
          짧게 말하면 AI가 거래 초안을 만들어 드립니다.
        </p>
        <div className="mt-7">
          <AiTransactionDraftForm
            householdMembers={householdMembers}
            onCreated={handleTransactionCreated}
          />
        </div>
        <div className="my-7 flex items-center gap-3 text-xs text-stone-400">
          <span className="h-px flex-1 bg-stone-200" />
          직접 입력
          <span className="h-px flex-1 bg-stone-200" />
        </div>
        <TransactionForm
          currentUserId={currentUser.id}
          householdMembers={householdMembers}
          onCreated={handleTransactionCreated}
        />
      </section>

      <section className="rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-emerald-700">최근 기록</p>
            <h2 className="mt-2 text-3xl font-semibold tracking-tight">거래 내역</h2>
          </div>
          <p className="text-sm text-stone-500">{transactions.length}건 표시</p>
        </div>

        <div className="mt-6 flex gap-2" aria-label="결제자 필터">
          {([
            ["all", "전체"],
            ["me", "내 결제"],
            ["partner", "배우자"],
          ] as const).map(([filter, label]) => (
            <button
              className={`rounded-full px-4 py-2 text-sm font-medium transition ${
                payerFilter === filter
                  ? "bg-emerald-700 text-white"
                  : "bg-stone-100 text-stone-600 hover:bg-stone-200"
              }`}
              key={filter}
              onClick={() => changePayerFilter(filter)}
              disabled={isChangingFilter || isLoadingMore}
              type="button"
            >
              {label}
            </button>
          ))}
        </div>

        {error ? (
          <p className="mt-8 rounded-2xl bg-amber-50 px-5 py-4 text-sm text-amber-800" role="alert">
            {error}
          </p>
        ) : null}

        {transactions.length === 0 && !error ? (
          <div className="mt-8 rounded-2xl border border-dashed border-stone-300 px-6 py-16 text-center">
            <p className="font-medium text-stone-700">아직 기록한 거래가 없습니다.</p>
            <p className="mt-2 text-sm text-stone-500">첫 거래를 왼쪽 폼에서 추가해 보세요.</p>
          </div>
        ) : transactions.length > 0 ? (
          <>
            <ul className="mt-8 divide-y divide-stone-200">
              {transactions.map((transaction) => (
                <li className="flex items-center justify-between gap-5 py-5" key={transaction.id}>
                {editingTransactionId === transaction.id ? (
                  <TransactionEditForm
                    householdMembers={householdMembers}
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
                      <p className="mt-1 text-sm text-stone-500">
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
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="font-semibold text-stone-900">
                        {amountFormatter.format(transaction.amount)}원
                      </p>
                      <button
                        className="mt-2 text-sm font-medium text-emerald-700 hover:text-emerald-800"
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
                className="mt-6 w-full rounded-xl border border-stone-300 px-4 py-3 text-sm font-medium text-stone-700 transition hover:bg-stone-50 disabled:cursor-not-allowed disabled:opacity-60"
                disabled={isLoadingMore || isChangingFilter}
                onClick={loadMoreTransactions}
                type="button"
              >
                {isLoadingMore ? "불러오는 중..." : "더 보기"}
              </button>
            ) : null}
          </>
        ) : null}
      </section>
    </div>
  );
}
