import Link from "next/link";

export function AiConsentNotice() {
  return (
    <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-4 text-sm leading-6 text-amber-950">
      <p>AI 기능을 사용하려면 OpenAI 국외 이전 선택 동의가 필요합니다.</p>
      <Link className="mt-2 inline-block font-semibold underline underline-offset-4" href="/privacy/settings">
        개인정보 설정 열기
      </Link>
    </div>
  );
}
