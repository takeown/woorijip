"use client";

import { useCallback, useEffect, useState } from "react";
import { AiTransactionDraftForm } from "./ai-transaction-draft-form";
import {
  paymentDetailsLabel,
  type CardIssuer,
  type StoredPaymentMethod,
} from "./payment-details";
import { TransactionForm, type HouseholdMember } from "./transaction-form";

type CurrentUser = {
  id: number;
  displayName: string;
  householdId: number;
};

type Transaction = {
  id: number;
  payerId: number;
  merchant: string;
  description: string | null;
  amount: number;
  category: string;
  paymentMethod: StoredPaymentMethod;
  cardIssuer: CardIssuer | null;
  occurredAt: string;
  createdAt: string;
};

type PayerFilter = "all" | "me" | "partner";

type CsrfToken = {
  token: string;
  headerName: string;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");
const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Seoul",
});

function authErrorMessage(authError: string): string {
  switch (authError) {
    case "not_allowed":
      return "허용되지 않은 Google 계정입니다.";
    case "session_expired":
      return "로그인 시간이 만료되었습니다. 다시 시도해 주세요.";
    default:
      return "Google 로그인을 완료하지 못했습니다. 다시 시도해 주세요.";
  }
}

export default function Home() {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>();
  const [householdMembers, setHouseholdMembers] = useState<HouseholdMember[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [payerFilter, setPayerFilter] = useState<PayerFilter>("all");
  const [error, setError] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    const authError = new URL(window.location.href).searchParams.get("authError");
    return authError ? authErrorMessage(authError) : null;
  });

  const loadTransactions = useCallback(async (filter: PayerFilter = "all") => {
    const response = await fetch(`${apiUrl}/transactions?payer=${filter}`, {
      credentials: "include",
      cache: "no-store",
    });

    if (!response.ok) {
      throw new Error("거래 내역을 불러오지 못했습니다.");
    }

    setTransactions(await response.json());
  }, []);

  const loadHouseholdMembers = useCallback(async () => {
    const response = await fetch(`${apiUrl}/households/current/members`, {
      credentials: "include",
      cache: "no-store",
    });

    if (!response.ok) {
      throw new Error("가구 구성원을 불러오지 못했습니다.");
    }

    setHouseholdMembers(await response.json());
  }, []);

  useEffect(() => {
    let active = true;
    const url = new URL(window.location.href);
    const authError = url.searchParams.get("authError");
    if (authError) {
      url.searchParams.delete("authError");
      window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
    }

    fetch(`${apiUrl}/auth/me`, {
      credentials: "include",
      cache: "no-store",
    })
      .then(async (response) => {
        if (!active) return;
        if (response.status === 401) {
          setCurrentUser(null);
          return;
        }
        if (!response.ok) {
          throw new Error("로그인 상태를 확인하지 못했습니다.");
        }

        const user: CurrentUser = await response.json();
        await Promise.all([loadTransactions(), loadHouseholdMembers()]);
        if (active) setCurrentUser(user);
      })
      .catch((caughtError: unknown) => {
        if (!active) return;
        setCurrentUser(null);
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : "API 연결을 확인해 주세요.",
        );
      });

    return () => {
      active = false;
    };
  }, [loadHouseholdMembers, loadTransactions]);

  async function changePayerFilter(filter: PayerFilter) {
    setPayerFilter(filter);
    setError(null);
    try {
      await loadTransactions(filter);
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : "거래 내역을 불러오지 못했습니다.",
      );
    }
  }

  async function logout() {
    try {
      const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, {
        credentials: "include",
      });
      if (!csrfResponse.ok) {
        throw new Error("로그아웃 요청을 준비하지 못했습니다.");
      }
      const csrfToken: CsrfToken = await csrfResponse.json();
      const response = await fetch(`${apiUrl}/auth/logout`, {
        method: "POST",
        credentials: "include",
        headers: { [csrfToken.headerName]: csrfToken.token },
      });
      if (!response.ok) {
        throw new Error("로그아웃하지 못했습니다.");
      }

      setCurrentUser(null);
      setHouseholdMembers([]);
      setTransactions([]);
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : "로그아웃하지 못했습니다.",
      );
    }
  }

  if (currentUser === undefined) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-stone-100 px-5 text-stone-700">
        <p>로그인 상태를 확인하고 있습니다.</p>
      </main>
    );
  }

  if (currentUser === null) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-stone-100 px-5 text-stone-900">
        <section className="w-full max-w-md rounded-3xl border border-stone-200 bg-white p-8 text-center shadow-sm">
          <p className="text-sm font-medium text-emerald-700">우리 둘의 생활 기록</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight">우리집</h1>
          <p className="mt-4 text-sm leading-6 text-stone-600">
            허용된 Google 계정으로 로그인해 주세요.
          </p>
          {error ? (
            <p className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
              {error}
            </p>
          ) : null}
          <a
            className="mt-7 block w-full rounded-xl bg-emerald-700 px-4 py-3 font-medium text-white transition hover:bg-emerald-800"
            href={`${apiUrl}/oauth2/authorization/google`}
          >
            Google 계정으로 로그인
          </a>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-stone-100 px-5 py-10 text-stone-900 sm:px-8">
      <div className="mx-auto mb-5 flex w-full max-w-6xl items-center justify-end gap-4 text-sm text-stone-600">
        <span>{currentUser.displayName}</span>
        <button
          className="font-medium text-emerald-700 hover:text-emerald-800"
          onClick={logout}
          type="button"
        >
          로그아웃
        </button>
      </div>
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
              onCreated={() => loadTransactions(payerFilter)}
            />
          </div>
          <div className="my-7 flex items-center gap-3 text-xs text-stone-400">
            <span className="h-px flex-1 bg-stone-200" />
            직접 입력
            <span className="h-px flex-1 bg-stone-200" />
          </div>
          <div>
            <TransactionForm
              currentUserId={currentUser.id}
              householdMembers={householdMembers}
              onCreated={() => loadTransactions(payerFilter)}
            />
          </div>
        </section>

        <section className="rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-emerald-700">최근 기록</p>
              <h2 className="mt-2 text-3xl font-semibold tracking-tight">거래 내역</h2>
            </div>
            <p className="text-sm text-stone-500">{transactions.length}건</p>
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
          ) : transactions.length === 0 ? (
            <div className="mt-8 rounded-2xl border border-dashed border-stone-300 px-6 py-16 text-center">
              <p className="font-medium text-stone-700">아직 기록한 거래가 없습니다.</p>
              <p className="mt-2 text-sm text-stone-500">첫 거래를 왼쪽 폼에서 추가해 보세요.</p>
            </div>
          ) : (
            <ul className="mt-8 divide-y divide-stone-200">
              {transactions.map((transaction) => (
                <li className="flex items-center justify-between gap-5 py-5" key={transaction.id}>
                  <div className="min-w-0">
                    <p className="truncate font-medium text-stone-900">{transaction.merchant}</p>
                    {transaction.description ? (
                      <p className="mt-1 truncate text-sm text-stone-700">
                        {transaction.description}
                      </p>
                    ) : null}
                    <p className="mt-1 text-sm text-stone-500">
                      {householdMembers.find((member) => member.userId === transaction.payerId)
                        ?.displayName ?? "알 수 없음"} · {transaction.category} ·{" "}
                      {paymentDetailsLabel(transaction.paymentMethod, transaction.cardIssuer)} ·{" "}
                      {dateFormatter.format(new Date(transaction.occurredAt))}
                    </p>
                  </div>
                  <p className="shrink-0 font-semibold text-stone-900">
                    {amountFormatter.format(transaction.amount)}원
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </main>
  );
}
