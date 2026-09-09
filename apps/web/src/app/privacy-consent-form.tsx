"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import type { PrivacyConsentStatus } from "./authenticated-shell";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function PrivacyConsentForm({
  initialStatus,
  onSaved,
  settings = false,
}: {
  initialStatus: PrivacyConsentStatus;
  onSaved: (status: PrivacyConsentStatus) => void;
  settings?: boolean;
}) {
  const [privacyAgreed, setPrivacyAgreed] = useState(initialStatus.privacyPolicyAgreed);
  const [aiAgreed, setAiAgreed] = useState(initialStatus.aiOverseasTransferAgreed);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!privacyAgreed || isSaving) return;
    setIsSaving(true);
    setError(null);
    try {
      const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, { credentials: "include" });
      if (!csrfResponse.ok) throw new Error("동의 요청을 준비하지 못했습니다.");
      const csrf: { token: string; headerName: string } = await csrfResponse.json();
      const response = await fetch(`${apiUrl}/auth/privacy-consents`, {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({
          privacyPolicyAgreed: privacyAgreed,
          aiOverseasTransferAgreed: aiAgreed,
        }),
      });
      if (!response.ok) throw new Error("동의 내용을 저장하지 못했습니다.");
      onSaved(await response.json());
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "동의 내용을 저장하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <form className="space-y-5" onSubmit={submit}>
      <label className="flex cursor-pointer items-start gap-3 rounded-2xl border border-stone-200 bg-white p-4">
        <input
          checked={privacyAgreed}
          className="mt-1 h-5 w-5 accent-emerald-700"
          disabled={settings}
          onChange={(event) => setPrivacyAgreed(event.target.checked)}
          type="checkbox"
        />
        <span>
          <span className="font-semibold text-stone-900">[필수] 개인정보 처리방침 동의</span>
          <span className="mt-1 block text-sm leading-6 text-stone-600">
            로그인, 가계부 저장과 서비스 운영에 필요한 개인정보 처리에 동의합니다.
          </span>
          <Link className="mt-2 inline-block text-sm font-medium text-emerald-700 underline underline-offset-4" href="/privacy">
            처리방침 전문 보기
          </Link>
        </span>
      </label>

      <label className="flex cursor-pointer items-start gap-3 rounded-2xl border border-stone-200 bg-white p-4">
        <input
          checked={aiAgreed}
          className="mt-1 h-5 w-5 accent-emerald-700"
          onChange={(event) => setAiAgreed(event.target.checked)}
          type="checkbox"
        />
        <span>
          <span className="font-semibold text-stone-900">[선택] OpenAI 국외 이전 동의</span>
          <span className="mt-1 block text-sm leading-6 text-stone-600">
            AI 기능을 사용할 때 질문, 제한된 거래 정보 또는 안전검사를 통과한 카드 이용내역 이미지를 미국의 OpenAI로 전송합니다. 남용 방지 목적으로 최대 30일 보관될 수 있습니다.
          </span>
          <span className="mt-2 block text-sm text-stone-600">
            동의하지 않아도 수동 입력과 조회는 사용할 수 있고, AI 입력·분석·캡처 기능만 제한됩니다.
          </span>
        </span>
      </label>

      {error ? <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">{error}</p> : null}
      <button
        className="min-h-12 w-full rounded-xl bg-emerald-700 px-4 py-3 font-semibold text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50"
        disabled={!privacyAgreed || isSaving}
        type="submit"
      >
        {isSaving ? "저장하고 있습니다" : settings ? "동의 설정 저장" : "동의하고 시작하기"}
      </button>
    </form>
  );
}
