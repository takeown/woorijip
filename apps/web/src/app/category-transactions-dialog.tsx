"use client";

import { useEffect, useRef, useState } from "react";
import type { SpendingPayer } from "./stats-url-state";
import type { HouseholdMember } from "./transaction-form";
import type { StoredValueAccount } from "./stored-value-account-panel";
import { TransactionEditForm, type EditableTransaction } from "./transaction-edit-form";

type TransactionPage = { items: EditableTransaction[]; nextCursor: string | null };
type Props = {
  category: string;
  label: string;
  periodLabel: string;
  from: string;
  to: string;
  payer: SpendingPayer;
  amount: number;
  count: number;
  onClose: () => void;
  onChanged: () => void;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");
const dayFormatter = new Intl.DateTimeFormat("ko-KR", {
  year: "numeric", month: "long", day: "numeric", timeZone: "Asia/Seoul",
});
const controlClass = "min-h-11 shrink-0 whitespace-nowrap rounded-xl px-3 text-sm font-medium text-accent-strong hover:bg-accent-soft focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:bg-accent-soft disabled:opacity-50";

export function CategoryTransactionsDialog(props: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [page, setPage] = useState<TransactionPage | null>(null);
  const [members, setMembers] = useState<HouseholdMember[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<EditableTransaction | null>(null);
  const [accounts, setAccounts] = useState<StoredValueAccount[] | null>(null);
  const [editorError, setEditorError] = useState<string | null>(null);
  const [editorLoading, setEditorLoading] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    const overflow = document.body.style.overflow;
    dialog?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      if (trigger instanceof HTMLElement) {
        const target = trigger.isConnected ? trigger : document.getElementById(trigger.id);
        target?.focus({ preventScroll: true });
      }
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams({
      category: props.category, payer: props.payer,
      from: props.from, to: props.to, size: "20",
    });
    if (cursor) params.set("cursor", cursor);
    const options = { cache: "no-store" as const, credentials: "include" as const, signal: controller.signal };
    async function load() {
      try {
        const [transactionsResponse, membersResponse] = await Promise.all([
          fetch(`${apiUrl}/transactions?${params}`, options),
          fetch(`${apiUrl}/households/current/members`, options),
        ]);
        if (!transactionsResponse.ok || !membersResponse.ok) throw new Error("거래내역을 불러오지 못했습니다.");
        const nextPage: TransactionPage = await transactionsResponse.json();
        const nextMembers: HouseholdMember[] = await membersResponse.json();
        if (controller.signal.aborted) return;
        setPage((previous) => ({
          items: cursor ? [...(previous?.items ?? []), ...nextPage.items] : nextPage.items,
          nextCursor: nextPage.nextCursor,
        }));
        setMembers(nextMembers);
      } catch (caughtError) {
        if (!controller.signal.aborted) setError(caughtError instanceof Error ? caughtError.message : "거래내역을 불러오지 못했습니다.");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    void load();
    return () => controller.abort();
  }, [props.category, props.payer, props.from, props.to, cursor, reload]);

  async function startEditing(transaction: EditableTransaction) {
    setEditing(transaction);
    setEditorError(null);
    if (accounts) return;
    setEditorLoading(true);
    try {
      const response = await fetch(`${apiUrl}/stored-value-accounts`, { cache: "no-store", credentials: "include" });
      if (!response.ok) throw new Error("거래 수정 정보를 불러오지 못했습니다.");
      setAccounts(await response.json());
    } catch {
      setEditorError("거래 수정 정보를 불러오지 못했습니다.");
    } finally {
      setEditorLoading(false);
    }
  }

  function refresh() {
    setEditing(null);
    setAccounts(null);
    setPage(null);
    setCursor(null);
    setError(null);
    setLoading(true);
    setReload((value) => value + 1);
    props.onChanged();
  }

  const groups = new Map<string, EditableTransaction[]>();
  for (const item of page?.items ?? []) {
    const day = dayFormatter.format(new Date(item.occurredAt));
    const transactions = groups.get(day) ?? [];
    transactions.push(item);
    groups.set(day, transactions);
  }

  return (
    <dialog
      aria-labelledby="category-transactions-title"
      className="fixed inset-x-0 bottom-0 top-auto m-0 max-h-[90dvh] w-full max-w-none overflow-y-auto overscroll-contain rounded-t-2xl border border-border-soft bg-surface p-0 text-foreground backdrop:bg-foreground/40 sm:inset-0 sm:m-auto sm:max-h-[85dvh] sm:max-w-2xl sm:rounded-2xl"
      onCancel={(event) => { event.preventDefault(); props.onClose(); }}
      ref={dialogRef}
    >
      <header className="sticky top-0 z-10 border-b border-border-soft bg-surface px-5 py-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-xl font-semibold [overflow-wrap:anywhere]" id="category-transactions-title">{props.label} 거래내역</h2>
            <p className="mt-1 font-ui text-sm text-stone-600 tabular-nums">{props.periodLabel} · {props.payer === "ALL" ? "전체" : props.payer === "ME" ? "나" : "배우자"}</p>
          </div>
          <button className={controlClass} onClick={props.onClose} type="button">닫기</button>
        </div>
        <p className="mt-3 font-ui text-2xl font-semibold tabular-nums">{amountFormatter.format(props.amount)}원 <span className="text-sm font-normal text-stone-600">· {props.count}건</span></p>
      </header>
      <div className="px-5 pt-2 pb-[max(1.25rem,env(safe-area-inset-bottom))]">
        {editing ? (
          <section className="py-4" aria-label="거래 수정">
            <button className={controlClass} onClick={() => setEditing(null)} type="button">목록으로</button>
            {editorLoading ? <p role="status" className="py-6 text-sm">거래 수정 정보를 불러오고 있습니다.</p>
              : editorError ? <div role="alert"><p>{editorError}</p><button className={controlClass} onClick={() => void startEditing(editing)} type="button">다시 시도</button></div>
              : accounts ? <TransactionEditForm key={editing.id} transaction={editing} householdMembers={members} storedValueAccounts={accounts} onCancel={() => setEditing(null)} onChanged={refresh} /> : null}
          </section>
        ) : (
          <>
            {[...groups].map(([day, transactions]) => (
              <section key={day} className="mt-5">
                <h3 className="border-b border-border-soft pb-2 font-ui text-sm text-stone-600 tabular-nums">{day}</h3>
                <ul className="divide-y divide-border-soft">
                  {transactions.map((transaction) => (
                    <li key={transaction.id}>
                      <button className="w-full min-w-0 rounded-lg py-4 text-left hover:bg-accent-soft focus-visible:outline-2 focus-visible:outline-focus active:bg-accent-soft" onClick={() => void startEditing(transaction)} type="button" aria-label={`${transaction.merchant} 거래 수정`}>
                        <span className="flex items-baseline justify-between gap-3">
                          <span className="min-w-0 font-medium [overflow-wrap:anywhere]">{transaction.merchant}</span>
                          <span className="shrink-0 font-ui font-semibold tabular-nums">{amountFormatter.format(transaction.amount)}원</span>
                        </span>
                        <span className="mt-1 block text-sm text-stone-600 [overflow-wrap:anywhere]">{members.find((member) => member.userId === transaction.payerId)?.displayName ?? "결제자"}{transaction.description ? ` · ${transaction.description}` : ""}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
            {loading ? <p className="py-8 text-sm text-stone-600" role="status">거래내역을 불러오고 있습니다.</p> : null}
            {error ? <div className="py-5" role="alert"><p>{error}</p><button className={controlClass} onClick={() => { setLoading(true); setError(null); setReload((value) => value + 1); }} type="button">다시 시도</button></div> : null}
            {!loading && !error && page?.items.length === 0 ? <p className="py-8 text-sm text-stone-600">이 기간에는 기록된 {props.label} 거래가 없습니다.</p> : null}
            {!error && page?.nextCursor ? <button className={`${controlClass} mt-4 w-full border border-border-soft`} disabled={loading} onClick={() => { setLoading(true); setCursor(page.nextCursor); }} type="button">거래 더 보기</button> : null}
          </>
        )}
      </div>
    </dialog>
  );
}
