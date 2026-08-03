"use client";

import { FormEvent, useState } from "react";
import {
  cardIssuers,
  type CardIssuer,
  type PaymentMethod,
} from "./payment-details";
import type { HouseholdMember } from "./transaction-form";
import { TransactionClassificationFields } from "./transaction-classification-fields";
import type {
  TransactionCategory,
  TransactionTag,
} from "./transaction-classification";
import type { StoredValueAccount } from "./stored-value-account-panel";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type ReadyDraft = {
  status: "READY";
  merchant: string;
  description: string | null;
  amount: number;
  category: TransactionCategory;
  tags: TransactionTag[];
  occurredAt: string;
  payerId: number;
  payerDisplayName: string;
  paymentMethod: PaymentMethod;
  cardIssuer: CardIssuer | null;
  storedValueAccountType: StoredValueAccount["type"] | null;
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

type ApiProblem = {
  detail?: unknown;
};

type AiTransactionDraftFormProps = {
  onCreated: () => Promise<void> | void;
  householdMembers: HouseholdMember[];
  storedValueAccounts?: StoredValueAccount[];
};

const maxAiRequests = 3;

export function AiTransactionDraftForm({
  onCreated,
  householdMembers,
  storedValueAccounts = [],
}: AiTransactionDraftFormProps) {
  const [message, setMessage] = useState("");
  const [clarification, setClarification] = useState("");
  const [messages, setMessages] = useState<string[]>([]);
  const [requestCount, setRequestCount] = useState(0);
  const [draft, setDraft] = useState<AiDraft | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CARD");
  const [cardIssuer, setCardIssuer] = useState<CardIssuer | "">("");
  const [storedValueAccountId, setStoredValueAccountId] = useState("");

  async function csrfToken(): Promise<CsrfToken> {
    const response = await fetch(`${apiUrl}/auth/csrf`, {
      credentials: "include",
    });
    if (!response.ok) {
      throw new Error("요청을 준비하지 못했습니다.");
    }
    return response.json();
  }

  async function requestDraft(nextMessages: string[]) {
    setIsGenerating(true);
    setError(null);

    try {
      const csrf = await csrfToken();
      setRequestCount((current) => current + 1);
      const response = await fetch(`${apiUrl}/ai/transaction-drafts`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({ messages: nextMessages }),
      });
      if (!response.ok) {
        let message = "AI 거래 초안을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.";
        if (response.status === 400) {
          const problem: ApiProblem | null = await response.json().catch(() => null);
          if (typeof problem?.detail === "string") {
            message = problem.detail;
          }
        }
        throw new Error(message);
      }

      const nextDraft: AiDraft = await response.json();
      setMessages(nextMessages);
      setDraft(nextDraft);
      if (nextDraft.status === "READY") {
        setPaymentMethod(nextDraft.paymentMethod);
        setCardIssuer(nextDraft.cardIssuer ?? "");
        const storedValueAccount = storedValueAccounts.find(
          (account) => account.type === nextDraft.storedValueAccountType,
        );
        setStoredValueAccountId(storedValueAccount ? String(storedValueAccount.id) : "");
      }
      return true;
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : "AI 거래 초안을 만들지 못했습니다.",
      );
      return false;
    } finally {
      setIsGenerating(false);
    }
  }

  async function generateDraft(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setDraft(null);
    setMessages([]);
    setRequestCount(0);
    setClarification("");
    await requestDraft([message.trim()]);
  }

  async function answerClarification(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draft?.status !== "NEEDS_CLARIFICATION" || requestCount >= maxAiRequests) return;

    const answer = clarification.trim();
    if (!answer) return;

    const succeeded = await requestDraft([...messages, answer]);
    if (succeeded) setClarification("");
  }

  function resetConversation() {
    setMessage("");
    setClarification("");
    setMessages([]);
    setRequestCount(0);
    setDraft(null);
    setError(null);
    setPaymentMethod("CARD");
    setCardIssuer("");
    setStoredValueAccountId("");
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
          description: String(formData.get("description") ?? "").trim() || null,
          amount: Number(formData.get("amount")),
          category: String(formData.get("category") ?? "").trim(),
          tags: formData.getAll("tags"),
          classificationSource: "AI",
          paymentMethod,
          cardIssuer: paymentMethod === "CARD" ? cardIssuer : null,
          storedValueAccountId: storedValueAccountId ? Number(storedValueAccountId) : null,
          occurredAt: new Date(String(formData.get("occurredAt"))).toISOString(),
        }),
      });
      if (!response.ok) {
        throw new Error("거래를 저장하지 못했습니다.");
      }

      resetConversation();
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
            <div>
              <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-description">
                내역 <span className="font-normal text-stone-500">(선택)</span>
              </label>
              <input
                className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                defaultValue={draft.description ?? ""}
                id="draft-description"
                maxLength={500}
                name="description"
                placeholder="세제와 휴지"
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
            </div>
            <TransactionClassificationFields
              defaultCategory={draft.category}
              defaultTags={draft.tags}
              idPrefix="draft"
            />
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
              <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-paymentMethod">
                결제 경로
              </label>
              <select
                className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                id="draft-paymentMethod"
                name="paymentMethod"
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
            </div>
            {paymentMethod === "CARD" ? (
              <div>
                <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-cardIssuer">
                  카드사
                </label>
                <select
                  className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                  id="draft-cardIssuer"
                  name="cardIssuer"
                  onChange={(event) => setCardIssuer(event.target.value as CardIssuer)}
                  required
                  value={cardIssuer}
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
              <label className="mb-1.5 block text-sm font-medium text-stone-700" htmlFor="draft-storedValueAccountId">
                사용 잔액 <span className="font-normal text-stone-500">(선택)</span>
              </label>
              <select
                className="w-full rounded-xl border border-emerald-200 bg-white px-3 py-2.5 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
                disabled={paymentMethod === "CASH"}
                id="draft-storedValueAccountId"
                onChange={(event) => setStoredValueAccountId(event.target.value)}
                required={paymentMethod === "QR"}
                value={storedValueAccountId}
              >
                <option value="">일반 결제</option>
                {storedValueAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name} · 잔액 {account.balance.toLocaleString("ko-KR")}원
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
                onClick={resetConversation}
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
      ) : draft?.status === "NEEDS_CLARIFICATION" ? (
        <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 p-5">
          <p className="text-sm font-medium text-amber-900" role="status">
            {draft.message}
          </p>
          {requestCount < maxAiRequests ? (
            <form className="mt-4 space-y-3" onSubmit={answerClarification}>
              <label className="block text-sm font-medium text-stone-700" htmlFor="ai-clarification">
                추가 답변
              </label>
              <input
                autoFocus
                className="w-full rounded-xl border border-amber-200 bg-white px-3 py-2.5 outline-none transition focus:border-amber-500 focus:ring-2 focus:ring-amber-100"
                id="ai-clarification"
                maxLength={500}
                onChange={(event) => setClarification(event.target.value)}
                placeholder="예: 8천원"
                required
                value={clarification}
              />
              <button
                className="w-full rounded-xl bg-amber-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-amber-800 disabled:opacity-60"
                disabled={isGenerating || clarification.trim().length === 0}
                type="submit"
              >
                {isGenerating ? "초안 보완 중..." : "답변하고 초안 보완"}
              </button>
            </form>
          ) : (
            <p className="mt-3 text-sm text-amber-800">
              추가 질문 한도에 도달했습니다. 내용을 보완해 처음부터 다시 입력해 주세요.
            </p>
          )}
          <button
            className="mt-3 w-full rounded-xl border border-stone-300 bg-white px-4 py-2.5 text-sm font-medium text-stone-700 hover:bg-stone-50"
            onClick={resetConversation}
            type="button"
          >
            처음부터 다시
          </button>
        </div>
      ) : draft ? (
        <div className="mt-5 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <p role="status">{draft.message}</p>
          <button
            className="mt-3 w-full rounded-xl border border-stone-300 bg-white px-4 py-2.5 font-medium text-stone-700 hover:bg-stone-50"
            onClick={resetConversation}
            type="button"
          >
            새 거래 입력
          </button>
        </div>
      ) : null}

      {requestCount > 0 ? (
        <p className="mt-3 text-center text-xs text-stone-500">
          AI 요청 {requestCount}/{maxAiRequests}회
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
