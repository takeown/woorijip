type Health = {
  status: string;
};

async function getApiHealth(): Promise<Health | null> {
  const apiUrl = process.env.API_URL ?? "http://localhost:8080";

  try {
    const response = await fetch(`${apiUrl}/health`, { cache: "no-store" });
    if (!response.ok) return null;

    return response.json();
  } catch {
    return null;
  }
}

export default async function Home() {
  const health = await getApiHealth();

  return (
    <main className="flex min-h-screen items-center justify-center bg-stone-50 px-6 text-stone-900">
      <section className="w-full max-w-xl rounded-3xl border border-stone-200 bg-white p-10 shadow-sm">
        <p className="mb-3 text-sm font-medium text-emerald-700">우리 둘의 생활 기록</p>
        <h1 className="text-4xl font-semibold tracking-tight">우리집</h1>
        <p className="mt-4 leading-7 text-stone-600">
          결제 내역을 자동으로 모으고, 우리 집 돈이 어디로 갔는지 편하게 물어보는 가계부.
        </p>
        <div className="mt-8 flex items-center gap-2 text-sm text-stone-500">
          <span
            className={`h-2.5 w-2.5 rounded-full ${health ? "bg-emerald-500" : "bg-amber-400"}`}
          />
          API {health ? "연결됨" : "연결 대기 중"}
        </div>
      </section>
    </main>
  );
}
