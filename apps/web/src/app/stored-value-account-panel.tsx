"use client";

import { FormEvent, useState } from "react";
import type { HouseholdMember } from "./transaction-form";

export type StoredValueAccountCategory =
  | "GIFT_CERTIFICATE"
  | "VOUCHER"
  | "LOCAL_CURRENCY"
  | "PREPAID"
  | "OTHER";

export type StoredValueAutomationKey =
  | "ONNURI_GIFT_CERTIFICATE"
  | "PREGNANCY_VOUCHER";

export type StoredValueAccount = {
  id: number;
  ownerUserId: number;
  ownerDisplayName: string;
  category: StoredValueAccountCategory;
  customCategoryName: string | null;
  automationKey: StoredValueAutomationKey | null;
  name: string;
  balance: number;
  archived: boolean;
  canDelete: boolean;
};

type Props = {
  accounts: StoredValueAccount[];
  householdMembers: HouseholdMember[];
  onChanged: () => Promise<void> | void;
};

type AccountPreset =
  | StoredValueAutomationKey
  | "CUSTOM_GIFT_CERTIFICATE"
  | "CUSTOM_VOUCHER"
  | "LOCAL_CURRENCY"
  | "PREPAID"
  | "OTHER";

type ApiProblem = { detail?: unknown };

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");
const categories: { value: StoredValueAccountCategory; label: string }[] = [
  { value: "GIFT_CERTIFICATE", label: "상품권" },
  { value: "VOUCHER", label: "바우처" },
  { value: "LOCAL_CURRENCY", label: "지역화폐" },
  { value: "PREPAID", label: "선불잔액" },
  { value: "OTHER", label: "직접 입력" },
];

export function StoredValueAccountPanel({ accounts, householdMembers, onChanged }: Props) {
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createPreset, setCreatePreset] = useState<AccountPreset>("CUSTOM_GIFT_CERTIFICATE");
  const [editCategories, setEditCategories] = useState<Record<number, StoredValueAccountCategory>>({});

  async function csrfToken() {
    const response = await fetch(`${apiUrl}/auth/csrf`, { credentials: "include" });
    if (!response.ok) throw new Error("요청을 준비하지 못했습니다.");
    return response.json() as Promise<{ token: string; headerName: string }>;
  }

  async function apiError(response: Response, fallback: string) {
    const problem: ApiProblem | null = await response.json().catch(() => null);
    return typeof problem?.detail === "string" ? problem.detail : fallback;
  }

  async function createAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSavingKey("create");
    setError(null);
    const form = event.currentTarget;
    const data = new FormData(form);
    const preset = String(data.get("preset")) as AccountPreset;
    const selection = presetDetails(preset);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/stored-value-accounts`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify({
          ownerUserId: Number(data.get("ownerUserId")),
          name: String(data.get("name") ?? "").trim(),
          category: selection.category,
          customCategoryName: selection.category === "OTHER"
            ? String(data.get("customCategoryName") ?? "").trim()
            : null,
          automationKey: selection.automationKey,
        }),
      });
      if (!response.ok) throw new Error(await apiError(response, "잔액 계정을 추가하지 못했습니다."));
      form.reset();
      setCreatePreset("CUSTOM_GIFT_CERTIFICATE");
      await onChanged();
      form.closest("details")?.removeAttribute("open");
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "잔액 계정을 추가하지 못했습니다.");
    } finally {
      setSavingKey(null);
    }
  }

  async function credit(event: FormEvent<HTMLFormElement>, account: StoredValueAccount) {
    event.preventDefault();
    setSavingKey(`credit-${account.id}`);
    setError(null);
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/stored-value-accounts/${account.id}/credits`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify({
          balanceAmount: Number(data.get("balanceAmount")),
          paidAmount: Number(data.get("paidAmount")),
          sourceName: String(data.get("sourceName") ?? "").trim() || null,
          occurredAt: new Date(String(data.get("occurredAt"))).toISOString(),
        }),
      });
      if (!response.ok) throw new Error(await apiError(response, "잔액을 추가하지 못했습니다."));
      form.reset();
      await onChanged();
      form.closest("details")?.removeAttribute("open");
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "잔액을 추가하지 못했습니다.");
    } finally {
      setSavingKey(null);
    }
  }

  async function adjust(event: FormEvent<HTMLFormElement>, account: StoredValueAccount) {
    event.preventDefault();
    setSavingKey(`adjust-${account.id}`);
    setError(null);
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/stored-value-accounts/${account.id}/adjustments`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify({
          direction: String(data.get("direction")),
          amount: Number(data.get("amount")),
          reason: String(data.get("reason") ?? "").trim(),
          occurredAt: new Date(String(data.get("occurredAt"))).toISOString(),
        }),
      });
      if (!response.ok) throw new Error(await apiError(response, "잔액을 조정하지 못했습니다."));
      form.reset();
      await onChanged();
      form.closest("details")?.removeAttribute("open");
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "잔액을 조정하지 못했습니다.");
    } finally {
      setSavingKey(null);
    }
  }

  async function updateAccount(
    account: StoredValueAccount,
    name: string,
    category: StoredValueAccountCategory,
    customCategoryName: string | null,
    archived: boolean,
    savingAction: string,
  ) {
    setSavingKey(`${savingAction}-${account.id}`);
    setError(null);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/stored-value-accounts/${account.id}`, {
        method: "PATCH",
        credentials: "include",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify({ name, category, customCategoryName, archived }),
      });
      if (!response.ok) throw new Error(await apiError(response, "잔액 계정을 수정하지 못했습니다."));
      await onChanged();
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "잔액 계정을 수정하지 못했습니다.");
    } finally {
      setSavingKey(null);
    }
  }

  async function saveDetails(event: FormEvent<HTMLFormElement>, account: StoredValueAccount) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const category = String(data.get("category")) as StoredValueAccountCategory;
    await updateAccount(
      account,
      String(data.get("name") ?? "").trim(),
      category,
      category === "OTHER" ? String(data.get("customCategoryName") ?? "").trim() : null,
      account.archived,
      "edit",
    );
  }

  async function deleteAccount(account: StoredValueAccount) {
    if (!window.confirm(`"${account.name}" 계정을 삭제할까요?`)) return;
    setSavingKey(`delete-${account.id}`);
    setError(null);
    try {
      const csrf = await csrfToken();
      const response = await fetch(`${apiUrl}/stored-value-accounts/${account.id}`, {
        method: "DELETE",
        credentials: "include",
        headers: { [csrf.headerName]: csrf.token },
      });
      if (!response.ok) throw new Error(await apiError(response, "잔액 계정을 삭제하지 못했습니다."));
      await onChanged();
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "잔액 계정을 삭제하지 못했습니다.");
    } finally {
      setSavingKey(null);
    }
  }

  return (
    <section className="mb-7 mt-5 rounded-2xl bg-stone-50 p-4">
      <div className="flex items-end justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-emerald-700">별도 잔액</p>
          <h2 className="mt-1 text-lg font-semibold">상품권·바우처</h2>
        </div>
        <span className="text-xs text-stone-500">{accounts.filter((account) => !account.archived).length}개 사용 중</span>
      </div>

      <details className="mt-3 rounded-xl border border-dashed border-emerald-300 bg-emerald-50/50">
        <summary className="cursor-pointer list-none px-4 py-3 text-sm font-medium text-emerald-800 [&::-webkit-details-marker]:hidden">
          잔액 계정 추가
        </summary>
        <form className="grid gap-3 border-t border-emerald-100 px-4 pb-4 pt-3" onSubmit={createAccount}>
          <label className="text-xs font-medium text-stone-600">
            이름
            <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" maxLength={100} name="name" placeholder="예: 서울사랑상품권" required />
          </label>
          <label className="text-xs font-medium text-stone-600">
            소유자
            <select className="mt-1 w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm" name="ownerUserId" required>
              {householdMembers.map((member) => <option key={member.userId} value={member.userId}>{member.displayName}</option>)}
            </select>
          </label>
          <label className="text-xs font-medium text-stone-600">
            종류
            <select className="mt-1 w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm" name="preset" onChange={(event) => setCreatePreset(event.target.value as AccountPreset)} value={createPreset}>
              <option value="ONNURI_GIFT_CERTIFICATE">온누리상품권</option>
              <option value="PREGNANCY_VOUCHER">임산부 바우처</option>
              <option value="CUSTOM_GIFT_CERTIFICATE">일반 상품권</option>
              <option value="CUSTOM_VOUCHER">일반 바우처</option>
              <option value="LOCAL_CURRENCY">지역화폐</option>
              <option value="PREPAID">선불잔액</option>
              <option value="OTHER">직접 입력</option>
            </select>
          </label>
          {createPreset === "OTHER" ? (
            <label className="text-xs font-medium text-stone-600">
              종류명
              <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" maxLength={40} name="customCategoryName" placeholder="예: 육아 지원금" required />
            </label>
          ) : null}
          <button className="rounded-lg bg-emerald-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-50" disabled={savingKey !== null} type="submit">
            {savingKey === "create" ? "추가 중..." : "잔액 추가"}
          </button>
        </form>
      </details>

      <div className="mt-3 space-y-2">
        {accounts.length === 0 ? <p className="rounded-xl bg-white px-4 py-5 text-center text-sm text-stone-500">필요한 상품권이나 바우처 계정을 추가해 주세요.</p> : null}
        {accounts.map((account) => (
          <details className="group overflow-hidden rounded-xl border border-stone-200 bg-white" key={account.id}>
            <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 [&::-webkit-details-marker]:hidden">
              <span className="min-w-0">
                <span className="block truncate text-xs text-stone-500">{account.ownerDisplayName} · {accountCategoryLabel(account)}{account.archived ? " · 보관됨" : ""}</span>
                <span className="mt-0.5 block truncate text-sm font-medium text-stone-700">{account.name}</span>
              </span>
              <span className="flex shrink-0 items-center gap-2">
                <span className={account.archived ? "font-semibold text-stone-500" : "font-semibold text-emerald-700"}>{amountFormatter.format(account.balance)}원</span>
                <span aria-hidden="true" className="text-stone-400 transition-transform group-open:rotate-180">▾</span>
              </span>
            </summary>

            {!account.archived ? (
              <>
                <form className="grid gap-3 border-t border-stone-100 px-4 pb-4 pt-3" onSubmit={(event) => credit(event, account)}>
                  <label className="text-xs font-medium text-stone-600">
                    잔액 추가 금액
                    <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" min="1" name="balanceAmount" required type="number" />
                  </label>
                  <label className="text-xs font-medium text-stone-600">
                    실제 출금액
                    <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" defaultValue={account.category === "VOUCHER" ? 0 : undefined} min="0" name="paidAmount" required type="number" />
                  </label>
                  <label className="text-xs font-medium text-stone-600">
                    출금 계좌·지급처 <span className="font-normal">(선택)</span>
                    <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" maxLength={100} name="sourceName" />
                  </label>
                  <label className="text-xs font-medium text-stone-600">
                    일시
                    <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" name="occurredAt" required type="datetime-local" />
                  </label>
                  <button className="rounded-lg bg-stone-800 px-3 py-2 text-sm font-medium text-white disabled:opacity-50" disabled={savingKey !== null} type="submit">
                    {savingKey === `credit-${account.id}` ? "저장 중..." : "잔액 추가"}
                  </button>
                </form>

                <details className="border-t border-stone-100 bg-amber-50/50">
                  <summary className="cursor-pointer list-none px-4 py-3 text-sm font-medium text-amber-800 [&::-webkit-details-marker]:hidden">잔액 조정</summary>
                  <form className="grid gap-3 border-t border-amber-100 px-4 pb-4 pt-3" onSubmit={(event) => adjust(event, account)}>
                    <p className="text-xs text-stone-500">잔액만 변경되며 소비 통계에는 포함되지 않습니다.</p>
                    <label className="text-xs font-medium text-stone-600">
                      조정 방향
                      <select className="mt-1 w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm" defaultValue="DECREASE" name="direction">
                        <option value="DECREASE">차감</option>
                        <option value="INCREASE">증가</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-stone-600">
                      조정 금액
                      <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" min="1" name="amount" required type="number" />
                    </label>
                    <label className="text-xs font-medium text-stone-600">
                      조정 사유
                      <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" maxLength={100} name="reason" placeholder="예: 누락 사용, 환불, 잔액 정정" required />
                    </label>
                    <label className="text-xs font-medium text-stone-600">
                      조정 일시
                      <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" name="occurredAt" required type="datetime-local" />
                    </label>
                    <button className="rounded-lg bg-amber-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-50" disabled={savingKey !== null} type="submit">
                      {savingKey === `adjust-${account.id}` ? "조정 중..." : "잔액 조정"}
                    </button>
                  </form>
                </details>
              </>
            ) : null}

            <form className="grid gap-3 border-t border-stone-100 bg-stone-50 px-4 pb-4 pt-3" onSubmit={(event) => saveDetails(event, account)}>
              <label className="text-xs font-medium text-stone-600">
                계정 이름
                <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" defaultValue={account.name} maxLength={100} name="name" required />
              </label>
              <label className="text-xs font-medium text-stone-600">
                분류
                <select className="mt-1 w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm disabled:bg-stone-100" disabled={account.automationKey !== null} name="category" onChange={(event) => setEditCategories((current) => ({ ...current, [account.id]: event.target.value as StoredValueAccountCategory }))} value={editCategories[account.id] ?? account.category}>
                  {categories.map((category) => <option key={category.value} value={category.value}>{category.label}</option>)}
                </select>
                {account.automationKey ? <input name="category" type="hidden" value={account.category} /> : null}
              </label>
              {(editCategories[account.id] ?? account.category) === "OTHER" ? (
                <label className="text-xs font-medium text-stone-600">
                  종류명
                  <input className="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 text-sm" defaultValue={account.customCategoryName ?? ""} maxLength={40} name="customCategoryName" required />
                </label>
              ) : null}
              <div className="grid grid-cols-2 gap-2">
                <button className="rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm font-medium text-stone-700 disabled:opacity-50" disabled={savingKey !== null} type="submit">정보 저장</button>
                <button className="rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm font-medium text-stone-700 disabled:opacity-50" disabled={savingKey !== null} onClick={() => updateAccount(account, account.name, account.category, account.customCategoryName, !account.archived, "archive")} type="button">{account.archived ? "다시 사용" : "보관"}</button>
              </div>
              {account.canDelete ? <button className="rounded-lg bg-red-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-50" disabled={savingKey !== null} onClick={() => deleteAccount(account)} type="button">삭제</button> : <p className="text-xs text-stone-500">사용 이력이 있어 삭제 대신 보관할 수 있습니다.</p>}
            </form>
          </details>
        ))}
      </div>
      {error ? <p className="mt-3 text-sm text-red-700" role="alert">{error}</p> : null}
    </section>
  );
}

function accountCategoryLabel(account: StoredValueAccount): string {
  return account.customCategoryName
    ?? categories.find((category) => category.value === account.category)?.label
    ?? account.category;
}

function presetDetails(preset: AccountPreset): {
  category: StoredValueAccountCategory;
  automationKey: StoredValueAutomationKey | null;
} {
  switch (preset) {
    case "ONNURI_GIFT_CERTIFICATE":
      return { category: "GIFT_CERTIFICATE", automationKey: preset };
    case "PREGNANCY_VOUCHER":
      return { category: "VOUCHER", automationKey: preset };
    case "CUSTOM_GIFT_CERTIFICATE":
      return { category: "GIFT_CERTIFICATE", automationKey: null };
    case "CUSTOM_VOUCHER":
      return { category: "VOUCHER", automationKey: null };
    default:
      return { category: preset, automationKey: null };
  }
}
