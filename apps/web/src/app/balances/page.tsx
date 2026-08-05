"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AuthenticatedShell } from "../authenticated-shell";
import {
  StoredValueAccountPanel,
  type StoredValueAccount,
} from "../stored-value-account-panel";
import type { HouseholdMember } from "../transaction-form";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function BalancesPage() {
  return (
    <AuthenticatedShell>
      {() => <BalanceManagementPage />}
    </AuthenticatedShell>
  );
}

export function BalanceManagementPage() {
  const [accounts, setAccounts] = useState<StoredValueAccount[]>([]);
  const [householdMembers, setHouseholdMembers] = useState<HouseholdMember[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAccounts = useCallback(async () => {
    const response = await fetch(`${apiUrl}/stored-value-accounts`, {
      cache: "no-store",
      credentials: "include",
    });
    if (!response.ok) throw new Error("잔액을 불러오지 못했습니다.");
    return response.json() as Promise<StoredValueAccount[]>;
  }, []);

  const fetchHouseholdMembers = useCallback(async () => {
    const response = await fetch(`${apiUrl}/households/current/members`, {
      cache: "no-store",
      credentials: "include",
    });
    if (!response.ok) throw new Error("가구 구성원을 불러오지 못했습니다.");
    return response.json() as Promise<HouseholdMember[]>;
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([fetchAccounts(), fetchHouseholdMembers()])
      .then(([nextAccounts, nextHouseholdMembers]) => {
        if (!active) return;
        setAccounts(nextAccounts);
        setHouseholdMembers(nextHouseholdMembers);
      })
      .catch((caughtError: unknown) => {
        if (!active) return;
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : "잔액 정보를 불러오지 못했습니다.",
        );
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [fetchAccounts, fetchHouseholdMembers]);

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-3xl rounded-2xl border border-stone-200 bg-white px-5 py-16 text-center text-stone-500 shadow-sm lg:rounded-3xl">
        잔액 정보를 불러오고 있습니다.
      </div>
    );
  }

  return (
    <section className="mx-auto w-full max-w-3xl rounded-2xl border border-stone-200 bg-white p-4 shadow-sm lg:rounded-3xl lg:p-7">
      <Link
        className="inline-flex min-h-11 items-center text-sm font-medium text-emerald-700 lg:hidden"
        href="/"
      >
        ← 거래로 돌아가기
      </Link>
      <p className="mt-2 text-sm font-medium text-emerald-700 lg:mt-0">우리집 결제 재원</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">잔액 관리</h1>
      <p className="mt-3 text-sm leading-6 text-stone-600">
        상품권·바우처·지역화폐의 충전, 지급과 잔액 조정을 관리합니다.
      </p>

      {error ? (
        <p className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : (
        <StoredValueAccountPanel
          accounts={accounts}
          householdMembers={householdMembers}
          onChanged={async () => setAccounts(await fetchAccounts())}
        />
      )}
    </section>
  );
}
