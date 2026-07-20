"use client";

import { useCallback, useEffect, useState } from "react";
import { TransactionForm } from "./transaction-form";

type CurrentUser = {
  id: number;
  displayName: string;
  householdId: number;
};

type Transaction = {
  id: number;
  merchant: string;
  amount: number;
  category: string;
  occurredAt: string;
  createdAt: string;
};

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

export default function Home() {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadTransactions = useCallback(async () => {
    const response = await fetch(`${apiUrl}/transactions`, {
      credentials: "include",
      cache: "no-store",
    });

    if (!response.ok) {
      throw new Error("거래 내역을 불러오지 못했습니다.");
    }

    setTransactions(await response.json());
  }, []);

  useEffect(() => {
    let active = true;

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

        setCurrentUser(await response.json());
        await loadTransactions();
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
  }, [loadTransactions]);

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
            오늘 사용한 금액을 직접 기록해 보세요.
          </p>
          <div className="mt-7">
            <TransactionForm onCreated={loadTransactions} />
          </div>
        </section>

        <section className="rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
          <div className="flex items-end justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-emerald-700">최근 기록</p>
              <h2 className="mt-2 text-3xl font-semibold tracking-tight">거래 내역</h2>
            </div>
            <p className="text-sm text-stone-500">{transactions.length}건</p>
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
                    <p className="mt-1 text-sm text-stone-500">
                      {transaction.category} · {dateFormatter.format(new Date(transaction.occurredAt))}
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
