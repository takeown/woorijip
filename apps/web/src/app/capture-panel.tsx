"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { transactionCategories, type TransactionCategory } from "./transaction-classification";
import { cardIssuers, type CardIssuer } from "./payment-details";
import type { HouseholdMember } from "./transaction-form";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const inputClass = "mt-2 min-h-11 w-full min-w-0 rounded-xl border border-border-soft bg-surface px-3 font-ui text-base tabular-nums focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:opacity-50";
const buttonClass = "min-h-11 rounded-xl bg-accent px-4 py-2 font-medium text-accent-ink transition-colors hover:bg-accent-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus active:translate-y-px disabled:opacity-50";

type Entry = { merchant: string | null; amount: number | null; occurredAt: string | null; category: TransactionCategory | null; status: "APPROVAL" | "REVIEW" };
type Row = { id: string; source: number; merchant: string; amount: string; occurredAt: string; category: TransactionCategory; categoryEdited: boolean; selected: boolean; review: boolean; allowDuplicate: boolean };
type Check = { duplicate: boolean; category: TransactionCategory | null };
type Batch = { requestId: string; payerId: number; cardIssuer: CardIssuer; candidates: { merchant: string; amount: number; occurredAt: string; category: TransactionCategory; allowDuplicate: boolean }[] };

async function post(path: string, body: FormData | Batch, signal?: AbortSignal): Promise<Response> {
  const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, { credentials: "include", signal });
  if (!csrfResponse.ok) throw new Error("로그인 상태를 확인한 뒤 다시 시도해 주세요.");
  const csrf: { headerName: string; token: string } = await csrfResponse.json();
  return fetch(`${apiUrl}${path}`, {
    method: "POST", credentials: "include", signal,
    headers: { [csrf.headerName]: csrf.token, ...(body instanceof FormData ? {} : { "Content-Type": "application/json" }) },
    body: body instanceof FormData ? body : JSON.stringify(body),
  });
}

async function errorMessage(response: Response): Promise<string> {
  const data: { message?: string } = await response.json().catch(() => ({}));
  return data.message ?? "처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function CapturePanel({ currentUserId }: { currentUserId: number }) {
  const [issuer, setIssuer] = useState<CardIssuer>("SHINHAN");
  const [year, setYear] = useState(String(new Date().getFullYear()));
  const [payer, setPayer] = useState(currentUserId);
  const [members, setMembers] = useState<HouseholdMember[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [checks, setChecks] = useState<Record<string, Check> | null>(null);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [failures, setFailures] = useState<string[]>([]);
  const [success, setSuccess] = useState<string | null>(null);
  const [retryPending, setRetryPending] = useState(false);
  const pending = useRef<Batch | null>(null);
  const controller = useRef<AbortController | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const selected = rows.filter((row) => row.selected);

  useEffect(() => {
    const abort = new AbortController();
    fetch(`${apiUrl}/households/current/members`, { credentials: "include", signal: abort.signal })
      .then(async (response) => { if (!response.ok) throw new Error(); setMembers(await response.json()); })
      .catch(() => { if (!abort.signal.aborted) setError("결제자 정보를 불러오지 못했습니다. 새로고침해 주세요."); });
    return () => { abort.abort(); controller.current?.abort(); };
  }, []);

  function edit(id: string, change: Partial<Row>) {
    setRows((current) => current.map((row) => row.id === id ? { ...row, ...change } : row));
    if (Object.keys(change).some((key) => key !== "allowDuplicate")) setChecks(null);
  }

  async function analyze() {
    if (busy || !files.length) return;
    setError(null); setSuccess(null); setFailures([]); setRows([]); setChecks(null); setBusy(true);
    controller.current = new AbortController();
    const { signal } = controller.current;
    const extracted: Row[] = [];
    const failed: string[] = [];
    try {
      for (const [index, file] of files.entries()) {
        setProgress(`${index + 1}/${files.length}장 분석 중`);
        try {
          const form = new FormData(); form.append("file", file);
          const params = new URLSearchParams({ cardIssuer: issuer, year });
          const response = await post(`/transaction-captures/analyze?${params}`, form, signal);
          if (!response.ok) throw new Error(await errorMessage(response));
          const data: { entries: Entry[] } = await response.json();
          if (!data.entries.length) failed.push(`${index + 1}번째 캡처: 읽을 수 있는 거래가 없습니다.`);
          for (const entry of data.entries) {
            extracted.push({ id: crypto.randomUUID(), source: index + 1, merchant: entry.merchant ?? "", amount: entry.amount?.toString() ?? "", occurredAt: entry.occurredAt?.slice(0, 16) ?? "", category: entry.category ?? "OTHER", categoryEdited: false, selected: entry.status === "APPROVAL", review: entry.status === "REVIEW", allowDuplicate: false });
          }
        } catch (caught) {
          if (signal.aborted) break;
          failed.push(`${index + 1}번째 캡처: ${caught instanceof Error ? caught.message : "분석하지 못했습니다."}`);
        }
      }
      if (!signal.aborted) {
        if (extracted.length > 50) { setError("거래가 50건을 넘었습니다. 캡처를 나눠 선택해 주세요."); }
        else setRows(extracted);
        setFailures(failed);
      }
    } finally { setBusy(false); setProgress(""); setFiles([]); if (fileInput.current) fileInput.current.value = ""; }
  }

  function batch(): Batch {
    if (!selected.length) throw new Error("저장할 거래를 선택해 주세요.");
    if (selected.some((row) => !row.merchant.trim() || row.merchant.length > 200 || !/^\d+$/.test(row.amount) || !Number.isSafeInteger(Number(row.amount)) || Number(row.amount) < 1 || !row.occurredAt || !Number.isFinite(Date.parse(`${row.occurredAt}:00+09:00`)))) throw new Error("선택한 거래의 가맹점·금액·날짜와 시간을 확인해 주세요.");
    return { requestId: crypto.randomUUID(), payerId: payer, cardIssuer: issuer, candidates: selected.map((row) => ({ merchant: row.merchant.trim(), amount: Number(row.amount), occurredAt: `${row.occurredAt}:00+09:00`, category: row.category, allowDuplicate: row.allowDuplicate })) };
  }

  async function check() {
    if (busy) return;
    setError(null); setBusy(true);
    try {
      const response = await post("/transaction-captures/check", batch());
      if (!response.ok) throw new Error(await errorMessage(response));
      const data: Check[] = await response.json();
      setChecks(Object.fromEntries(selected.map((row, index) => [row.id, data[index]])));
      setRows((current) => current.map((row) => {
        const index = selected.findIndex((item) => item.id === row.id);
        const category = data[index]?.category;
        return category && !row.categoryEdited ? { ...row, category } : row;
      }));
    } catch (caught) { setError(caught instanceof Error ? caught.message : "중복을 확인하지 못했습니다."); }
    finally { setBusy(false); }
  }

  async function save() {
    if (busy) return;
    setBusy(true); setError(null);
    try {
      const request = pending.current ?? batch();
      pending.current = request;
      setRetryPending(true);
      const response = await post("/transaction-captures/apply", request);
      if (!response.ok) {
        if (response.status < 500) { pending.current = null; setRetryPending(false); setChecks(null); }
        throw new Error(await errorMessage(response));
      }
      const data: { savedCount: number } = await response.json();
      setSuccess(`${data.savedCount}건을 저장했습니다.`); setRows([]); setChecks(null); setFailures([]); pending.current = null; setRetryPending(false);
    } catch (caught) { setError(caught instanceof Error ? caught.message : "응답을 확인하지 못했습니다. 같은 요청으로 다시 저장해 주세요."); }
    finally { setBusy(false); }
  }

  return <section className="mx-auto w-full max-w-3xl min-w-0 pb-10">
    <Link className="inline-flex min-h-11 items-center text-sm text-accent-strong focus-visible:outline-2 focus-visible:outline-focus" href="/">거래 입력으로 돌아가기</Link>
    <h1 className="mt-4 text-3xl font-semibold">카드 내역 캡처</h1>
    <p className="mt-3 text-sm leading-7">같은 카드사의 캡처를 한 번에 5장까지 선택하세요. 민감정보 안전 검사를 통과한 원본 캡처를 OpenAI로 분석하고, 확인한 거래만 저장합니다.</p>
    <p className="mt-2 text-sm leading-7">현재는 신한·국민·현대카드의 마스킹된 원화 일시불 내역을 지원해요. 이름이나 전체 카드·계좌번호가 보이는 화면은 선택하지 마세요. 사진은 보관하지 않으며, 우리집 기준 하루 20장까지 분석할 수 있어요.</p>
    <fieldset disabled={busy || retryPending || rows.length > 0} className="mt-6 grid min-w-0 gap-4 sm:grid-cols-3">
      <label className="min-w-0 text-sm">카드사<select className={inputClass} value={issuer} onChange={(event) => setIssuer(event.target.value as CardIssuer)}>{cardIssuers.filter((item) => ["SHINHAN", "KB_KOOKMIN", "HYUNDAI"].includes(item.value)).map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
      <label className="min-w-0 text-sm">기준 연도<input className={inputClass} type="number" min={2000} max={2100} value={year} onChange={(event) => setYear(event.target.value)} /></label>
      <label className="min-w-0 text-sm">결제자<select className={inputClass} value={payer} onChange={(event) => { setPayer(Number(event.target.value)); setChecks(null); }}>{members.map((member) => <option key={member.userId} value={member.userId}>{member.displayName}</option>)}</select></label>
    </fieldset>
    {!rows.length && <div className="mt-6 border-y border-border-soft py-5">
      <label className="block text-sm" htmlFor="capture-files">캡처 선택 · PNG/JPEG, 장당 4MB 이하</label>
      <input ref={fileInput} id="capture-files" className="mt-3 min-h-11 w-full min-w-0 font-ui text-sm file:mr-3 file:min-h-11 file:rounded-xl file:border-0 file:bg-surface-muted file:px-4 file:text-foreground focus-visible:outline-2 focus-visible:outline-focus" type="file" accept="image/png,image/jpeg" multiple disabled={busy} onChange={(event) => {
        const next = Array.from(event.target.files ?? []);
        setError(null); setSuccess(null);
        if (next.length > 5 || next.some((file) => file.size === 0 || file.size > 4 * 1024 * 1024 || !["image/png", "image/jpeg"].includes(file.type))) { setError("PNG·JPEG 캡처를 장당 4MB 이하, 최대 5장까지 선택해 주세요."); setFiles([]); event.target.value = ""; return; }
        setFiles(next);
      }} />
      <button className={`${buttonClass} mt-4`} type="button" disabled={busy || !files.length || !members.length || Number(year) < 2000 || Number(year) > 2100} onClick={() => void analyze()}>{busy ? progress || "처리 중…" : `${files.length || "선택한"}장 분석하기`}</button>
      {busy && <button className="ml-3 min-h-11 px-3 text-accent-strong focus-visible:outline-2 focus-visible:outline-focus" type="button" onClick={() => controller.current?.abort()}>중단</button>}
    </div>}
    <p role="status" className="mt-4 text-sm text-accent-strong">{success ?? progress}</p>
    {error && <p role="alert" className="mt-4 rounded-xl border border-border-soft bg-surface-muted p-4 text-sm leading-6">{error}</p>}
    {failures.length > 0 && <ul aria-label="분석하지 못한 캡처" className="mt-4 space-y-2 text-sm">{failures.map((failure) => <li key={failure}>{failure}</li>)}</ul>}
    {rows.length > 0 && <>
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-b border-border-soft pb-4"><h2 className="text-xl font-semibold">인식한 거래 {rows.length}건</h2><button className="min-h-11 px-3 text-sm text-accent-strong focus-visible:outline-2 focus-visible:outline-focus disabled:opacity-50" type="button" disabled={busy || retryPending} onClick={() => { setRows([]); setChecks(null); }}>다른 캡처 선택</button></div>
      <p className="mt-3 text-sm leading-6">금액은 할인 전 승인금액입니다. 빠진 거래가 없는지 원본과 비교하고 날짜·분류를 확인해 주세요. 취소·할부·충전 등은 여기서 저장하지 않고 직접 입력에서 확인해 주세요.</p>
      <fieldset disabled={busy || retryPending} className="min-w-0">
        {rows.map((row, index) => <div key={row.id} className="min-w-0 border-b border-border-soft py-5">
          <label className="flex min-h-11 items-center gap-3 text-sm"><input className="h-5 w-5 accent-accent" type="checkbox" checked={row.selected} disabled={row.review} onChange={(event) => edit(row.id, { selected: event.target.checked })} />{index + 1}번 거래 · 캡처 {row.source}</label>
          {row.review && <p className="mb-3 text-sm">추가 확인이 필요한 거래 · 일괄 저장에서 제외</p>}
          <div className="grid min-w-0 gap-4 sm:grid-cols-2">
            <label className="min-w-0 text-sm">가맹점<input className={inputClass} maxLength={200} value={row.merchant} onChange={(event) => edit(row.id, { merchant: event.target.value, allowDuplicate: false })} /></label>
            <label className="min-w-0 text-sm">금액(원)<input className={inputClass} type="text" inputMode="numeric" value={row.amount} onChange={(event) => edit(row.id, { amount: event.target.value, allowDuplicate: false })} /></label>
            <label className="min-w-0 text-sm">결제일시<input className={inputClass} type="datetime-local" value={row.occurredAt} onChange={(event) => edit(row.id, { occurredAt: event.target.value, allowDuplicate: false })} /></label>
            <label className="min-w-0 text-sm">분류<select className={inputClass} value={row.category} onChange={(event) => edit(row.id, { category: event.target.value as TransactionCategory, categoryEdited: true })}>{transactionCategories.map((category) => <option key={category.value} value={category.value}>{category.label}</option>)}</select></label>
          </div>
          {checks?.[row.id]?.duplicate && <label className="mt-4 flex min-h-11 items-center gap-3 text-sm leading-6"><input className="h-5 w-5 shrink-0 accent-accent" type="checkbox" checked={row.allowDuplicate} onChange={(event) => edit(row.id, { allowDuplicate: event.target.checked })} />날짜·가맹점·금액이 같은 거래가 있지만 별도 결제임을 확인했어요.</label>}
        </div>)}
      </fieldset>
      <div className="mt-6 flex flex-wrap gap-3">
        {!retryPending && <button className={buttonClass} type="button" disabled={busy || !selected.length} onClick={() => void check()}>중복·분류 확인</button>}
        {(checks || retryPending) && <button className={buttonClass} type="button" disabled={busy || (!retryPending && selected.some((row) => checks?.[row.id]?.duplicate && !row.allowDuplicate))} onClick={() => void save()}>{retryPending ? "같은 요청으로 다시 저장" : `${selected.length}건 확인하고 저장`}</button>}
      </div>
      {retryPending && <p className="mt-3 text-sm leading-6">저장 결과를 확인할 때까지 내용 수정을 잠시 막았어요. 다시 저장해도 같은 요청은 한 번만 반영됩니다.</p>}
    </>}
  </section>;
}
