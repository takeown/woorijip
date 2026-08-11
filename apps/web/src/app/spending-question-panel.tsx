/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V5 */
/* Hallmark · component: spending question · genre: modern-minimal · theme: Woorijip custom */
"use client";

import { FormEvent, useState } from "react";

type SpendingQuestionStatus = "ANSWERED" | "UNSUPPORTED";

type SpendingEvidenceTransaction = {
  id: number;
  merchant: string;
  amount: number;
  occurredAt: string;
  payerLabel: string;
};

type SpendingQuestionAnswer = {
  status: SpendingQuestionStatus;
  message: string;
  evidenceTransactions: SpendingEvidenceTransaction[];
  remainingRequestsToday: number;
};

type CsrfToken = {
  token: string;
  headerName: string;
};

type ApiProblem = {
  detail?: unknown;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const amountFormatter = new Intl.NumberFormat("ko-KR");
const evidenceDateFormatter = new Intl.DateTimeFormat("ko-KR", {
  day: "numeric",
  month: "long",
  timeZone: "Asia/Seoul",
});
const suggestions = [
  "이번 달 식비 얼마 썼어?",
  "지난달보다 교통비 늘었어?",
  "이번 달 가장 큰 지출은 뭐야?",
];

export function SpendingQuestionPanel() {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<SpendingQuestionAnswer | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  async function askQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion || isLoading) return;

    setIsLoading(true);
    setAnswer(null);
    setError(null);

    try {
      const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, {
        credentials: "include",
      });
      if (!csrfResponse.ok) {
        throw new Error("질문 요청을 준비하지 못했습니다.");
      }
      const csrf: CsrfToken = await csrfResponse.json();
      const response = await fetch(`${apiUrl}/statistics/spending/questions`, {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({ question: trimmedQuestion }),
      });
      if (!response.ok) {
        const problem: ApiProblem | null = await response.json().catch(() => null);
        throw new Error(
          typeof problem?.detail === "string"
            ? problem.detail
            : "가계 질문에 답하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        );
      }

      setAnswer(await response.json());
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : "가계 질문에 답하지 못했습니다.",
      );
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <section
      aria-labelledby="spending-question-title"
      className="mt-6 border-y border-border-soft bg-surface-muted px-4 py-6 sm:px-5"
    >
      <div className="max-w-3xl">
        <h3 className="text-xl font-semibold text-foreground" id="spending-question-title">
          우리집 지출에 물어보기
        </h3>
        <p className="mt-2 text-sm text-stone-700">
          기간별 총지출, 카테고리 지출과 가장 큰 지출을 물어볼 수 있습니다. 질문 횟수는
          하루 단위로 제한됩니다.
        </p>
      </div>

      <form className="mt-5" onSubmit={askQuestion}>
        <label className="sr-only" htmlFor="spending-question">
          가계 지출 질문
        </label>
        <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
          <input
            className="min-h-12 min-w-0 rounded-xl border border-border-soft bg-surface px-4 py-3 text-base outline-2 outline-offset-2 outline-transparent transition-colors duration-150 ease-[var(--ease-out)] placeholder:text-stone-500 hover:bg-background focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isLoading}
            id="spending-question"
            maxLength={200}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="예: 이번 달 식비 얼마 썼어?"
            required
            value={question}
          />
          <button
            className="min-h-12 whitespace-nowrap rounded-xl bg-accent px-5 py-3 font-medium text-accent-ink transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-surface-muted active:bg-accent-strong disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isLoading || question.trim().length === 0}
            type="submit"
          >
            {isLoading ? "답을 찾는 중..." : "질문하기"}
          </button>
        </div>

        <div className="mt-3 flex flex-wrap gap-2" aria-label="질문 예시">
          {suggestions.map((suggestion) => (
            <button
              className="min-h-11 rounded-full border border-border-soft bg-surface px-4 py-2 text-left text-sm text-stone-700 transition-colors duration-150 ease-[var(--ease-out)] hover:bg-accent-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-surface-muted active:bg-accent-soft disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isLoading}
              key={suggestion}
              onClick={() => setQuestion(suggestion)}
              type="button"
            >
              {suggestion}
            </button>
          ))}
        </div>
      </form>

      {isLoading ? (
        <p className="mt-5 text-sm text-stone-700" role="status">
          질문을 안전한 조회 조건으로 바꾸고 있습니다.
        </p>
      ) : error ? (
        <p className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      ) : answer ? (
        <div className="mt-6 border-t border-border-soft pt-5" aria-live="polite">
          <p className="text-lg font-semibold leading-relaxed text-stone-900">{answer.message}</p>
          {answer.evidenceTransactions.length > 0 ? (
            <div className="mt-5">
              <h4 className="text-sm font-semibold text-stone-900">이 답변의 근거</h4>
              <ul className="mt-2 divide-y divide-border-soft">
                {answer.evidenceTransactions.map((transaction) => (
                  <li
                    className="flex items-baseline justify-between gap-4 py-3"
                    key={transaction.id}
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-stone-800">
                        {transaction.merchant}
                      </p>
                      <p className="mt-1 font-ui text-xs text-stone-600 tabular-nums">
                        {evidenceDateFormatter.format(new Date(transaction.occurredAt))} ·{" "}
                        {transaction.payerLabel}
                      </p>
                    </div>
                    <p className="shrink-0 font-ui text-sm font-semibold text-stone-900 tabular-nums">
                      {amountFormatter.format(transaction.amount)}원
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          <p className="mt-4 font-ui text-xs text-stone-600 tabular-nums">
            오늘 {answer.remainingRequestsToday}번 더 질문할 수 있습니다.
          </p>
        </div>
      ) : null}
    </section>
  );
}
