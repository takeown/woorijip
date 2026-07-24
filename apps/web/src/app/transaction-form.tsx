"use client";

import { FormEvent, useState } from "react";
import { cardIssuers, type PaymentMethod } from "./payment-details";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type HouseholdMember = {
  userId: number;
  displayName: string;
};

type TransactionFormProps = {
  onCreated: () => Promise<void> | void;
  currentUserId: number;
  householdMembers: HouseholdMember[];
};

type CsrfToken = {
  token: string;
  headerName: string;
};

export function TransactionForm({
  onCreated,
  currentUserId,
  householdMembers,
}: TransactionFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CARD");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setError(null);

    const form = event.currentTarget;
    const formData = new FormData(form);
    const occurredAt = String(formData.get("occurredAt") ?? "").trim();

    try {
      const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, {
        credentials: "include",
      });
      if (!csrfResponse.ok) {
        throw new Error("거래 저장 요청을 준비하지 못했습니다.");
      }
      const csrfToken: CsrfToken = await csrfResponse.json();
      const response = await fetch(`${apiUrl}/transactions`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrfToken.headerName]: csrfToken.token,
        },
        body: JSON.stringify({
          payerId: Number(formData.get("payerId")),
          merchant: formData.get("merchant"),
          description: String(formData.get("description") ?? "").trim() || null,
          amount: Number(formData.get("amount")),
          category: formData.get("category"),
          paymentMethod,
          cardIssuer: paymentMethod === "CARD" ? formData.get("cardIssuer") : null,
          occurredAt: occurredAt
            ? new Date(occurredAt).toISOString()
            : new Date().toISOString(),
        }),
      });

      if (!response.ok) {
        throw new Error("거래를 저장하지 못했습니다. 입력값을 확인해 주세요.");
      }

      form.reset();
      await onCreated();
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : "거래를 저장하지 못했습니다.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="space-y-5" onSubmit={handleSubmit}>
      <div>
        <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="payerId">
          결제자
        </label>
        <select
          className="w-full rounded-xl border border-stone-300 bg-white px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          defaultValue={currentUserId}
          id="payerId"
          name="payerId"
          required
        >
          {householdMembers.map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.displayName}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="merchant">
          가맹점
        </label>
        <input
          className="w-full rounded-xl border border-stone-300 px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          id="merchant"
          name="merchant"
          placeholder="김밥천국"
          required
          maxLength={200}
        />
      </div>

      <div>
        <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="description">
          내역 <span className="font-normal text-stone-500">(선택)</span>
        </label>
        <input
          className="w-full rounded-xl border border-stone-300 px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          id="description"
          name="description"
          placeholder="세제와 휴지"
          maxLength={500}
        />
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="amount">
            금액
          </label>
          <input
            className="w-full rounded-xl border border-stone-300 px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
            id="amount"
            name="amount"
            type="number"
            min="1"
            step="1"
            inputMode="numeric"
            placeholder="8000"
            required
          />
        </div>

        <div>
          <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="category">
            카테고리
          </label>
          <input
            className="w-full rounded-xl border border-stone-300 px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
            id="category"
            name="category"
            placeholder="식비"
            required
            maxLength={100}
          />
        </div>
      </div>

      <div>
        <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="paymentMethod">
          결제수단
        </label>
        <select
          className="w-full rounded-xl border border-stone-300 bg-white px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          id="paymentMethod"
          name="paymentMethod"
          onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}
          value={paymentMethod}
        >
          <option value="CARD">카드</option>
          <option value="CASH">현금</option>
        </select>
      </div>

      {paymentMethod === "CARD" ? (
        <div>
          <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="cardIssuer">
            카드사
          </label>
          <select
            className="w-full rounded-xl border border-stone-300 bg-white px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
            defaultValue=""
            id="cardIssuer"
            name="cardIssuer"
            required
          >
            <option disabled value="">카드사를 선택해 주세요</option>
            {cardIssuers.map((issuer) => (
              <option key={issuer.value} value={issuer.value}>
                {issuer.label}
              </option>
            ))}
          </select>
        </div>
      ) : null}

      <div>
        <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="occurredAt">
          결제 시각
        </label>
        <input
          className="w-full rounded-xl border border-stone-300 px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          id="occurredAt"
          name="occurredAt"
          type="datetime-local"
        />
        <p className="mt-2 text-xs text-stone-500">비워두면 현재 시각으로 저장됩니다.</p>
      </div>

      {error ? (
        <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : null}

      <button
        className="w-full rounded-xl bg-emerald-700 px-4 py-3 font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
        type="submit"
        disabled={isSubmitting}
      >
        {isSubmitting ? "저장 중..." : "거래 저장"}
      </button>
    </form>
  );
}
