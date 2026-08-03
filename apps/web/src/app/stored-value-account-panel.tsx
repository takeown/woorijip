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
      form.closest("details")?.removeAttribute("open");
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "잔액을 추가하지 못했습니다.");
    } finally {
      setIsSaving(null);
    }
  }

  return (
    <section className="mb-7 mt-5 rounded-2xl bg-stone-50 p-4">
      <div className="flex items-end justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-emerald-700">별도 잔액</p>
          <h2 className="mt-1 text-lg font-semibold">상품권·바우처</h2>
        </div>
        <p className="pb-0.5 text-xs text-stone-500">눌러서 충전·지급</p>
      </div>
      <div className="mt-3 space-y-2">
        {accounts.map((account) => (
          <details className="group overflow-hidden rounded-xl border border-stone-200 bg-white" key={account.id}>
            <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 [&::-webkit-details-marker]:hidden">
              <span className="min-w-0 text-sm font-medium text-stone-700">{account.name}</span>
              <span className="flex shrink-0 items-center gap-2">
                <span className="font-semibold text-emerald-700">
                  {amountFormatter.format(account.balance)}원
                </span>
                <span
                  aria-hidden="true"
                  className="text-stone-400 transition-transform group-open:rotate-180"
                >
                  ▾
                </span>
              </span>
            </summary>
            <form
              className="border-t border-stone-100 px-4 pb-4 pt-3"
              onSubmit={(event) => credit(event, account)}
            >
              <div className="grid gap-3">
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
              <button className="mt-3 w-full rounded-lg bg-stone-800 px-3 py-2 text-sm font-medium text-white disabled:opacity-50" disabled={isSaving !== null} type="submit">
                {isSaving === account.id ? "저장 중..." : account.type === "PREGNANCY_VOUCHER" ? "지급 기록" : "충전 기록"}
              </button>
            </form>
          </details>
        ))}
      </div>
      {error ? <p className="text-sm text-red-700" role="alert">{error}</p> : null}
    </section>
  );
}
