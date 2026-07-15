import { TransactionForm } from "./transaction-form";

type Transaction = {
  id: number;
  merchant: string;
  amount: number;
  category: string;
  occurredAt: string;
  createdAt: string;
};

type TransactionResult =
  | { transactions: Transaction[]; error: null }
  | { transactions: []; error: string };

const apiUrl = process.env.API_URL ?? "http://localhost:8080";

async function getTransactions(): Promise<TransactionResult> {
  try {
    const response = await fetch(`${apiUrl}/transactions`, { cache: "no-store" });

    if (!response.ok) {
      return { transactions: [], error: "거래 내역을 불러오지 못했습니다." };
    }

    return { transactions: await response.json(), error: null };
  } catch {
    return { transactions: [], error: "API 연결을 확인해 주세요." };
  }
}

const amountFormatter = new Intl.NumberFormat("ko-KR");
const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Seoul",
});

export default async function Home() {
  const result = await getTransactions();

  return (
    <main className="min-h-screen bg-stone-100 px-5 py-10 text-stone-900 sm:px-8">
      <div className="mx-auto grid w-full max-w-6xl gap-8 lg:grid-cols-[380px_1fr]">
        <section className="h-fit rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
          <p className="text-sm font-medium text-emerald-700">우리 둘의 생활 기록</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">거래 입력</h1>
          <p className="mt-3 text-sm leading-6 text-stone-600">
            오늘 사용한 금액을 직접 기록해 보세요.
          </p>
          <div className="mt-7">
            <TransactionForm />
          </div>
        </section>

        <section className="rounded-3xl border border-stone-200 bg-white p-7 shadow-sm">
          <div className="flex items-end justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-emerald-700">최근 기록</p>
              <h2 className="mt-2 text-3xl font-semibold tracking-tight">거래 내역</h2>
            </div>
            <p className="text-sm text-stone-500">{result.transactions.length}건</p>
          </div>

          {result.error ? (
            <p className="mt-8 rounded-2xl bg-amber-50 px-5 py-4 text-sm text-amber-800" role="alert">
              {result.error}
            </p>
          ) : result.transactions.length === 0 ? (
            <div className="mt-8 rounded-2xl border border-dashed border-stone-300 px-6 py-16 text-center">
              <p className="font-medium text-stone-700">아직 기록한 거래가 없습니다.</p>
              <p className="mt-2 text-sm text-stone-500">첫 거래를 왼쪽 폼에서 추가해 보세요.</p>
            </div>
          ) : (
            <ul className="mt-8 divide-y divide-stone-200">
              {result.transactions.map((transaction) => (
                <li className="flex items-center justify-between gap-5 py-5" key={transaction.id}>
                  <div className="min-w-0">
                    <p className="truncate font-medium text-stone-900">{transaction.merchant}</p>
                    <p className="mt-1 text-sm text-stone-500">
                      {transaction.category} · {dateFormatter.format(new Date(transaction.occurredAt))}
                    </p>
                  </div>
                  <p className="shrink-0 font-semibold text-stone-900">
                    {amountFormatter.format(transaction.amount)}원
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </main>
  );
}
