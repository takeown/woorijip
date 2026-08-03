"use client";

import { FocusEvent, FormEvent, useRef, useState } from "react";
import { cardIssuers, type PaymentMethod } from "./payment-details";
import { TransactionClassificationFields } from "./transaction-classification-fields";
import type {
  TransactionCategory,
  TransactionTag,
} from "./transaction-classification";
import type { StoredValueAccount } from "./stored-value-account-panel";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type HouseholdMember = {
  userId: number;
  displayName: string;
};

type TransactionFormProps = {
  onCreated: () => Promise<void> | void;
  currentUserId: number;
  householdMembers: HouseholdMember[];
  storedValueAccounts?: StoredValueAccount[];
};

type CsrfToken = {
  token: string;
  headerName: string;
};

type MerchantClassificationRecommendation = {
  ruleId: number;
  category: TransactionCategory;
  tags: TransactionTag[];
  source: "MERCHANT_RULE";
};

export function TransactionForm({
  onCreated,
  currentUserId,
  householdMembers,
  storedValueAccounts = [],
}: TransactionFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("CARD");
  const [storedValueAccountId, setStoredValueAccountId] = useState("");
  const [classificationKey, setClassificationKey] = useState(0);
  const [recommendedCategory, setRecommendedCategory] =
    useState<TransactionCategory>();
  const [recommendedTags, setRecommendedTags] = useState<TransactionTag[]>([]);
  const [appliedRuleId, setAppliedRuleId] = useState<number | null>(null);
  const [recommendationMessage, setRecommendationMessage] = useState<
    string | null
  >(null);
  const recommendationRequest = useRef(0);
  const classificationRevision = useRef(0);

  async function recommendClassification(event: FocusEvent<HTMLInputElement>) {
    const merchant = event.currentTarget.value.trim();
    if (!merchant) {
      return;
    }
    const requestId = recommendationRequest.current + 1;
    const revision = classificationRevision.current;
    recommendationRequest.current = requestId;

    try {
      const response = await fetch(
        `${apiUrl}/merchant-classification-rules/recommendation?merchant=${encodeURIComponent(merchant)}`,
        {
          credentials: "include",
          cache: "no-store",
        },
      );
      if (
        requestId !== recommendationRequest.current ||
        revision !== classificationRevision.current
      ) {
        return;
      }
      if (response.status === 204) {
        if (appliedRuleId !== null) {
          setRecommendedCategory(undefined);
          setRecommendedTags([]);
          setClassificationKey((current) => current + 1);
        }
        setAppliedRuleId(null);
        setRecommendationMessage(null);
        return;
      }
      if (!response.ok) {
        throw new Error();
      }

      const recommendation: MerchantClassificationRecommendation =
        await response.json();
      if (
        requestId !== recommendationRequest.current ||
        revision !== classificationRevision.current
      ) {
        return;
      }
      setRecommendedCategory(recommendation.category);
      setRecommendedTags(recommendation.tags);
      setAppliedRuleId(recommendation.ruleId);
      setClassificationKey((current) => current + 1);
      setRecommendationMessage("이 가맹점에 저장된 분류를 적용했습니다.");
    } catch {
      if (
        requestId !== recommendationRequest.current ||
        revision !== classificationRevision.current
      ) {
        return;
      }
      setRecommendationMessage("가맹점 분류 추천을 불러오지 못했습니다.");
    }
  }

  function markClassificationChanged() {
    classificationRevision.current += 1;
    if (appliedRuleId !== null) {
      setAppliedRuleId(null);
      setRecommendationMessage("추천을 수정해 이번 거래의 분류로 사용합니다.");
    }
  }

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
          tags: formData.getAll("tags"),
          classificationSource: "USER",
          classificationRuleId: appliedRuleId,
          saveMerchantRule: formData.get("saveMerchantRule") === "on",
          paymentMethod,
          cardIssuer: paymentMethod === "CARD" ? formData.get("cardIssuer") : null,
          storedValueAccountId: storedValueAccountId ? Number(storedValueAccountId) : null,
          occurredAt: occurredAt
            ? new Date(occurredAt).toISOString()
            : new Date().toISOString(),
        }),
      });

      if (!response.ok) {
        throw new Error("거래를 저장하지 못했습니다. 입력값을 확인해 주세요.");
      }

      form.reset();
      setRecommendedCategory(undefined);
      setRecommendedTags([]);
      setAppliedRuleId(null);
      setRecommendationMessage(null);
      setStoredValueAccountId("");
      setClassificationKey((current) => current + 1);
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
          onBlur={recommendClassification}
          onChange={() => {
            recommendationRequest.current += 1;
            if (appliedRuleId !== null) {
              setRecommendedCategory(undefined);
              setRecommendedTags([]);
              setClassificationKey((current) => current + 1);
            }
            setAppliedRuleId(null);
            setRecommendationMessage(null);
          }}
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

      <TransactionClassificationFields
        defaultCategory={recommendedCategory}
        defaultTags={recommendedTags}
        idPrefix="transaction"
        key={`transaction-classification-${classificationKey}`}
        onUserChange={markClassificationChanged}
      />

      {recommendationMessage ? (
        <p className="text-sm text-stone-600" role="status">
          {recommendationMessage}
        </p>
      ) : null}

      <label className="flex items-start gap-3 rounded-xl border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-700">
        <input
          className="mt-0.5 h-4 w-4 rounded border-stone-300 text-emerald-700 focus:ring-emerald-600"
          name="saveMerchantRule"
          type="checkbox"
        />
        <span>
          앞으로 이 가맹점에도 같은 카테고리와 태그 적용
        </span>
      </label>

      <div>
        <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="paymentMethod">
          결제 경로
        </label>
        <select
          className="w-full rounded-xl border border-stone-300 bg-white px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          id="paymentMethod"
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
        <label className="mb-2 block text-sm font-medium text-stone-700" htmlFor="storedValueAccountId">
          지출 계정 <span className="font-normal text-stone-500">(선택)</span>
        </label>
        <select
          className="w-full rounded-xl border border-stone-300 bg-white px-4 py-3 outline-none transition focus:border-emerald-600 focus:ring-2 focus:ring-emerald-100"
          disabled={paymentMethod === "CASH"}
          id="storedValueAccountId"
          onChange={(event) => setStoredValueAccountId(event.target.value)}
          value={storedValueAccountId}
        >
          <option value="">일반 결제</option>
          {storedValueAccounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.name} · 잔액 {account.balance.toLocaleString("ko-KR")}원
            </option>
          ))}
        </select>
        <p className="mt-2 text-xs text-stone-500">카드는 연결 카드, QR은 스캔 결제 경로를 뜻합니다.</p>
      </div>

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
