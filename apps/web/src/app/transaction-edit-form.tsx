"use client";

import { FormEvent, useState } from "react";
import {
  cardIssuers,
  type CardIssuer,
  type PaymentMethod,
  type StoredPaymentMethod,
} from "./payment-details";
import { TransactionClassificationFields } from "./transaction-classification-fields";
import type {
  TransactionCategory,
  TransactionTag,
} from "./transaction-classification";
import type { HouseholdMember } from "./transaction-form";
import type { StoredValueAccount } from "./stored-value-account-panel";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type EditableTransaction = {
  id: number;
  payerId: number;
  merchant: string;
  description: string | null;
  amount: number;
  category: TransactionCategory;
  tags: TransactionTag[];
  paymentMethod: StoredPaymentMethod;
  cardIssuer: CardIssuer | null;
  storedValueAccountId: number | null;
  occurredAt: string;
  createdAt: string;
  updatedAt: string;
};

type CsrfToken = {
  token: string;
  headerName: string;
};

type TransactionEditFormProps = {
  transaction: EditableTransaction;
  householdMembers: HouseholdMember[];
  storedValueAccounts?: StoredValueAccount[];
  onCancel: () => void;
  onChanged: () => Promise<void> | void;
};

export function TransactionEditForm({
  transaction,
  householdMembers,
  storedValueAccounts = [],
  onCancel,
  onChanged,
}: TransactionEditFormProps) {
  const initialPaymentMethod =
    transaction.paymentMethod === "UNKNOWN" ? "CARD" : transaction.paymentMethod;
  const [paymentMethod, setPaymentMethod] =
    useState<PaymentMethod>(initialPaymentMethod);
  const [storedValueAccountId, setStoredValueAccountId] = useState(
    transaction.storedValueAccountId?.toString() ?? "",
  );
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function csrfToken(): Promise<CsrfToken> {
    const response = await fetch(`${apiUrl}/auth/csrf`, {
      credentials: "include",
    });
    if (!response.ok) {
      throw new Error("요청을 준비하지 못했습니다.");
    }
    return response.json();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);
    setError(null);
    const formData = new FormData(event.currentTarget);

    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/transactions/${transaction.id}`, {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({
          expectedUpdatedAt: transaction.updatedAt,
          payerId: Number(formData.get("payerId")),
          merchant: String(formData.get("merchant") ?? "").trim(),
          description: String(formData.get("description") ?? "").trim() || null,
          amount: Number(formData.get("amount")),
          category: formData.get("category"),
          tags: formData.getAll("tags"),
          paymentMethod,
          cardIssuer: paymentMethod === "CARD" ? formData.get("cardIssuer") : null,
          storedValueAccountId: storedValueAccountId ? Number(storedValueAccountId) : null,
          occurredAt: new Date(String(formData.get("occurredAt"))).toISOString(),
        }),
      });
      if (!response.ok) {
        throw new Error(
          response.status === 409
            ? "다른 변경이 반영되었습니다. 새로고침 후 다시 수정해 주세요."
            : "거래를 수정하지 못했습니다.",
        );
      }
      await onChanged();
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : "거래를 수정하지 못했습니다.",
      );
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm(`"${transaction.merchant}" 거래를 삭제할까요?`)) {
      return;
    }
    setIsDeleting(true);
    setError(null);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/transactions/${transaction.id}`, {
        method: "DELETE",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({ expectedUpdatedAt: transaction.updatedAt }),
      });
      if (!response.ok) {
        throw new Error(
          response.status === 409
            ? "다른 변경이 반영되었습니다. 새로고침 후 다시 시도해 주세요."
            : "거래를 삭제하지 못했습니다.",
        );
      }
      await onChanged();
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : "거래를 삭제하지 못했습니다.",
      );
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <form className="w-full space-y-4 rounded-2xl bg-stone-50 p-5" onSubmit={handleSubmit}>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="text-sm font-medium text-stone-700">
          가맹점
          <input
            className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
            defaultValue={transaction.merchant}
            maxLength={200}
            name="merchant"
            required
          />
        </label>
        <label className="text-sm font-medium text-stone-700">
          금액
          <input
            className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
            defaultValue={transaction.amount}
            min="1"
            name="amount"
            required
            step="1"
            type="number"
          />
        </label>
      </div>

      <label className="block text-sm font-medium text-stone-700">
        내역 <span className="font-normal text-stone-500">(선택)</span>
        <input
          className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
          defaultValue={transaction.description ?? ""}
          maxLength={500}
          name="description"
        />
      </label>

      <TransactionClassificationFields
        defaultCategory={transaction.category}
        defaultTags={transaction.tags}
        idPrefix={`edit-${transaction.id}`}
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="text-sm font-medium text-stone-700">
          결제자
          <select
            className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
            defaultValue={transaction.payerId}
            name="payerId"
          >
            {householdMembers.map((member) => (
              <option key={member.userId} value={member.userId}>
                {member.displayName}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm font-medium text-stone-700">
          결제 경로
          <select
            className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
            onChange={(event) => {
              const method = event.target.value as PaymentMethod;
              setPaymentMethod(method);
              if (method === "CASH") setStoredValueAccountId("");
            }}
            value={paymentMethod}
          >
            <option value="CARD">카드</option>
            <option value="CASH">현금</option>
            <option value="QR">QR</option>
          </select>
        </label>
      </div>

      {paymentMethod === "CARD" ? (
        <label className="block text-sm font-medium text-stone-700">
          카드사
          <select
            className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
            defaultValue={transaction.cardIssuer ?? ""}
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
        </label>
      ) : null}

      <label className="block text-sm font-medium text-stone-700">
        지출 계정 <span className="font-normal text-stone-500">(선택)</span>
        <select
          className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
          disabled={paymentMethod === "CASH"}
          onChange={(event) => setStoredValueAccountId(event.target.value)}
          value={storedValueAccountId}
        >
          <option value="">일반 결제</option>
          {storedValueAccounts.filter((account) =>
            !account.archived || account.id === transaction.storedValueAccountId,
          ).map((account) => (
            <option key={account.id} value={account.id}>
              {account.ownerDisplayName} · {account.name} · 잔액 {account.balance.toLocaleString("ko-KR")}원
            </option>
          ))}
        </select>
      </label>

      <label className="block text-sm font-medium text-stone-700">
        결제 시각
        <input
          className="mt-2 w-full rounded-xl border border-stone-300 bg-white px-3 py-2.5"
          defaultValue={localDateTime(transaction.occurredAt)}
          name="occurredAt"
          required
          type="datetime-local"
        />
      </label>

      {error ? (
        <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap justify-between gap-3">
        <button
          className="rounded-xl px-4 py-2.5 text-sm font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
          disabled={isSaving || isDeleting}
          onClick={handleDelete}
          type="button"
        >
          {isDeleting ? "삭제 중..." : "거래 삭제"}
        </button>
        <div className="flex gap-2">
          <button
            className="rounded-xl border border-stone-300 px-4 py-2.5 text-sm font-medium text-stone-700"
            disabled={isSaving || isDeleting}
            onClick={onCancel}
            type="button"
          >
            취소
          </button>
          <button
            className="rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
            disabled={isSaving || isDeleting}
            type="submit"
          >
            {isSaving ? "저장 중..." : "수정 저장"}
          </button>
        </div>
      </div>
    </form>
  );
}

function localDateTime(value: string) {
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
