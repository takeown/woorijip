"use client";

import { FormEvent, useState } from "react";
import type { HouseholdMember } from "./transaction-form";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type ReadyDraft = {
  status: "READY";
  merchant: string;
  amount: number;
  category: string;
  occurredAt: string;
  payerId: number;
  payerDisplayName: string;
  message: string;
};

type MessageDraft = {
  status: "NEEDS_CLARIFICATION" | "UNSUPPORTED";
  message: string;
};

type AiDraft = ReadyDraft | MessageDraft;

type CsrfToken = {
  token: string;
  headerName: string;
};

type AiTransactionDraftFormProps = {
  onCreated: () => Promise<void> | void;
  householdMembers: HouseholdMember[];
};

export function AiTransactionDraftForm({
  onCreated,
  householdMembers,
}: AiTransactionDraftFormProps) {
  const [message, setMessage] = useState("");
  const [draft, setDraft] = useState<AiDraft | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
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

  async function generateDraft(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsGenerating(true);
    setDraft(null);
    setError(null);

    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/ai/transaction-drafts`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({ message }),
      });
      if (!response.ok) {
        throw new Error("AI 거래 초안을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
      }

      setDraft(await response.json());
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : "AI 거래 초안을 만들지 못했습니다.",
      );
    } finally {
      setIsGenerating(false);
    }
  }

  async function saveDraft(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draft?.status !== "READY") return;

    setIsSaving(true);
    setError(null);
    const formData = new FormData(event.currentTarget);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/transactions`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({
          payerId: Number(formData.get("payerId")),
          merchant: String(formData.get("merchant") ?? "").trim(),
          amount: Number(formData.get("amount")),
          category: String(formData.get("category") ?? "").trim(),
          occurredAt: new Date(String(formData.get("occurredAt"))).toISOString(),
        }),
      });
      if (!response.ok) {
        throw new Error("거래를 저장하지 못했습니다.");
      }

      setMessage("");
      setDraft(null);
      await onCreated();
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : "거래를 저장하지 못했습니다.",
      );
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div>
      <form className="space-y-4" onSubmit={generateDraft}>
        <div>
          <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="ai-message">
            자연어로 입력
          </label>
          <textarea
            className="min-h-28 w-full resize-y rounded-xl border border-stone-300 px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
            id="ai-message"
            maxLength={500}
            onChange={(event) => setMessage(event.target.value)}
            placeholder="오늘 김밥천국에서 내가 8천원 썼어"
            required
            value={message}
          />
        </div>
        <button
          className="w-full rounded-xl bg-emerald-700 px-4 py-3 font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={isGenerating || message.trim().length === 0}
          type="submit"
        >
          {isGenerating ? "초안 만드는 중..." : "AI로 거래 초안 만들기"}
        </button>
      </form>

      {draft?.status === "READY" ? (
        <div className="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50 p-5">
          <p className="text-sm font-medium text-emerald-800">{draft.message}</p>
          <form className="mt-4 space-y-4" onSubmit={saveDraft}>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-merchant">
                가맹점
              </label>
              <input
                className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                defaultValue={draft.merchant}
                id="draft-merchant"
                maxLength={200}
                name="merchant"
                required
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-amount">
                  금액
                </label>
                <input
                  className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                  defaultValue={draft.amount}
                  id="draft-amount"
                  inputMode="numeric"
                  min="1"
                  name="amount"
                  required
                  step="1"
                  type="number"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-category">
                  카테고리
                </label>
                <input
                  className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                  defaultValue={draft.category}
                  id="draft-category"
                  maxLength={100}
                  name="category"
                  required
                />
              </div>
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-payerId">
                결제자
              </label>
              <select
                className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                defaultValue={draft.payerId}
                id="draft-payerId"
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
              <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-occurredAt">
                결제 시각
              </label>
              <input
                className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                defaultValue={draft.occurredAt.slice(0, 16)}
                id="draft-occurredAt"
                name="occurredAt"
                required
                type="datetime-local"
              />
            </div>
            <div className="grid grid-cols-2 gap-3 pt-1">
              <button
                className="rounded-xl border border-stone-300 bg-white px-4 py-2.5 text-sm font-medium text-stone-700 hover:bg-stone-50"
                onClick={() => setDraft(null)}
                type="button"
              >
                취소
              </button>
              <button
                className="rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
                disabled={isSaving}
                type="submit"
              >
                {isSaving ? "저장 중..." : "확인하고 저장"}
              </button>
            </div>
          </form>
        </div>
      ) : draft ? (
        <p className="mt-5 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800" role="status">
          {draft.message}
        </p>
      ) : null}

      {error ? (
        <p className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
