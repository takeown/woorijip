"use client";

import { FormEvent, useState } from "react";

export type StoredValueAccount = {
  id: number;
  type: "ONNURI_GIFT_CERTIFICATE" | "PREGNANCY_VOUCHER";
  name: string;
  balance: number;
};

type Props = {
  accounts: StoredValueAccount[];
  onChanged: () => Promise<void> | void;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");

export function StoredValueAccountPanel({ accounts, onChanged }: Props) {
  const [isSaving, setIsSaving] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function credit(event: FormEvent<HTMLFormElement>, account: StoredValueAccount) {
    event.preventDefault();
    setIsSaving(account.id);
    setError(null);
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, { credentials: "include" });
      if (!csrfResponse.ok) throw new Error("요청을 준비하지 못했습니다.");
      const csrf: { token: string; headerName: string } = await csrfResponse.json();
      const response = await fetch(`${apiUrl}/stored-value-accounts/${account.id}/credits`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify({
          balanceAmount: Number(data.get("balanceAmount")),
          paidAmount: account.type === "PREGNANCY_VOUCHER" ? 0 : Number(data.get("paidAmount")),
          sourceName: String(data.get("sourceName") ?? "").trim() || null,
          occurredAt: new Date(String(data.get("occurredAt"))).toISOString(),
        }),
      });
      if (!response.ok) throw new Error("잔액을 추가하지 못했습니다. 입력값을 확인해 주세요.");
      form.reset();
      await onChanged();
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "잔액을 추가하지 못했습니다.");
    } finally {
      setIsSaving(null);
    }
  }

  return (
    <section className="mb-7 space-y-4 rounded-2xl bg-stone-50 p-5">
      <div>
        <p className="text-sm font-medium text-emerald-700">별도 잔액</p>
        <h2 className="mt-1 text-xl font-semibold">상품권·바우처</h2>
      </div>
      {accounts.map((account) => (
        <form className="rounded-xl border border-stone-200 bg-white p-4" key={account.id} onSubmit={(event) => credit(event, account)}>
          <div className="flex items-center justify-between gap-3">
            <p className="font-medium text-stone-800">{account.name}</p>
            <p className="font-semibold text-emerald-700">{amountFormatter.format(account.balance)}원</p>
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-stone-600">
              {account.type === "PREGNANCY_VOUCHER" ? "지급 금액" : "충전 금액"}
              <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" min="1" name="balanceAmount" required type="number" />
            </label>
            {account.type === "ONNURI_GIFT_CERTIFICATE" ? (
              <label className="text-xs font-medium text-stone-600">
                계좌 출금액
                <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" min="0" name="paidAmount" required type="number" />
              </label>
            ) : null}
            <label className="text-xs font-medium text-stone-600">
              출금 계좌 <span className="font-normal">(선택)</span>
              <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" maxLength={100} name="sourceName" />
            </label>
            <label className="text-xs font-medium text-stone-600">
              일시
              <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" name="occurredAt" required type="datetime-local" />
            </label>
          </div>
          <button className="mt-3 rounded-lg bg-stone-800 px-3 py-2 text-sm font-medium text-white disabled:opacity-50" disabled={isSaving !== null} type="submit">
            {isSaving === account.id ? "저장 중..." : account.type === "PREGNANCY_VOUCHER" ? "지급 기록" : "충전 기록"}
          </button>
        </form>
      ))}
      {error ? <p className="text-sm text-red-700" role="alert">{error}</p> : null}
    </section>
  );
}
