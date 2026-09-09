import Link from "next/link";

export const dynamic = "force-dynamic";

export default function PrivacyPage() {
  const controllerName = process.env.PRIVACY_CONTROLLER_NAME ?? "우리집 서비스 운영자";
  const contact = process.env.PRIVACY_CONTACT_EMAIL ?? "로그인 허용 사용자 간 직접 연락";
  return (
    <main className="min-h-screen bg-stone-100 px-4 py-8 text-stone-900 sm:px-6">
      <article className="mx-auto max-w-3xl rounded-3xl border border-stone-200 bg-white px-5 py-8 shadow-sm sm:px-10">
        <p className="text-sm font-medium text-emerald-700">시행일 2026년 9월 9일</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">개인정보 처리방침</h1>
        <p className="mt-5 leading-7 text-stone-700">
          우리집 서비스 운영자는 부부가 함께 쓰는 비공개 가계부를 제공하기 위해 아래와 같이 개인정보를 처리합니다.
        </p>

        <PolicySection title="1. 처리하는 개인정보와 목적">
          <PolicyList items={[
            "로그인 정보: Google 계정 식별자, 이메일, 표시 이름을 계정 확인과 접근 제어에 사용합니다.",
            "가계부 정보: 가구 구성, 거래 일시·가맹점·금액·분류·결제수단, 잔액과 변동을 가계부 기능 제공에 사용합니다.",
            "업로드 정보: 카드 명세서와 카드 앱 이용내역 이미지를 거래 후보 추출과 확인에 사용합니다.",
            "운영 정보: 로그인 세션과 AI 기능의 날짜별 사용 횟수를 보안과 사용량 제한에 사용합니다.",
          ]} />
        </PolicySection>

        <PolicySection title="2. 보유 기간과 삭제">
          <p>계정과 가계부 정보는 서비스를 사용하는 동안 보관하며, 이용 종료 또는 삭제 요청 시 법적 보존 의무가 없는 한 삭제합니다. 로그인 세션은 만료되거나 로그아웃하면 삭제됩니다.</p>
          <p className="mt-3">카드 명세서 원본, 카드 앱 이미지, OCR 결과, AI 질문과 답변은 우리집 서버에 영구 저장하지 않습니다. 사용자가 확정한 거래와 재처리 방지를 위한 비식별 지문·처리 결과만 저장할 수 있습니다.</p>
        </PolicySection>

        <PolicySection title="3. 제3자 처리와 국외 이전">
          <p>AI 기능을 선택한 경우에만 다음 정보가 암호화된 HTTPS 방식으로 국외 이전됩니다.</p>
          <dl className="mt-4 grid gap-3 rounded-2xl bg-stone-50 p-4 text-sm sm:grid-cols-[9rem_1fr]">
            <dt className="font-semibold">이전받는 자</dt><dd>OpenAI OpCo, LLC (privacy@openai.com)</dd>
            <dt className="font-semibold">국가</dt><dd>미국</dd>
            <dt className="font-semibold">시기·방법</dt><dd>사용자가 AI 기능을 실행할 때, 암호화된 네트워크 전송</dd>
            <dt className="font-semibold">이전 항목</dt><dd>AI 질문, 필요한 최소 거래 필드, 안전검사를 통과한 카드 이용내역 이미지, 가명화된 안전 식별자</dd>
            <dt className="font-semibold">목적</dt><dd>거래 초안 생성, 가계 지출 설명, 카드 이용내역 구조화와 서비스 남용 방지</dd>
            <dt className="font-semibold">보유 기간</dt><dd>OpenAI 애플리케이션 상태 저장은 끄며, 남용 방지 로그는 기본적으로 최대 30일 보관될 수 있습니다.</dd>
          </dl>
          <p className="mt-4">국외 이전에 동의하지 않거나 동의를 철회하면 AI 기능만 사용할 수 없습니다. 개인정보 설정에서 언제든 선택 동의를 변경할 수 있으며 수동 입력과 조회는 계속 사용할 수 있습니다.</p>
        </PolicySection>

        <PolicySection title="4. 안전조치">
          <PolicyList items={[
            "허용된 Google 계정만 로그인할 수 있도록 제한합니다.",
            "가계 데이터는 인증된 사용자가 속한 가구 범위 안에서만 조회하고 변경합니다.",
            "외부 AI 전송 전 카드번호, 계좌번호, 주민등록번호, 인증정보 등 금지정보를 서버에서 검사해 거부합니다.",
            "비밀값과 원본 업로드 내용을 애플리케이션 로그에 남기지 않습니다.",
          ]} />
        </PolicySection>

        <PolicySection title="5. 이용자의 권리와 문의">
          <p>이용자는 자신의 개인정보 열람, 정정, 삭제, 처리정지와 AI 국외 이전 동의 철회를 요청할 수 있습니다. 개인정보 설정에서 AI 동의를 변경하거나 우리집 서비스 운영자에게 직접 요청해 주세요. 요청을 받으면 본인 확인 후 지체 없이 처리합니다.</p>
          <dl className="mt-4 grid gap-2 rounded-2xl bg-stone-50 p-4 text-sm sm:grid-cols-[10rem_1fr]">
            <dt className="font-semibold">개인정보 보호책임자</dt><dd>{controllerName}</dd>
            <dt className="font-semibold">문의 방법</dt><dd>{contact}</dd>
          </dl>
        </PolicySection>

        <PolicySection title="6. 방침 변경">
          <p>처리 항목, 목적, 보유 기간 또는 국외 이전 내용이 달라지면 새 시행일과 변경 내용을 알리고, 필요한 경우 다시 동의를 받습니다.</p>
        </PolicySection>

        <div className="mt-10 flex flex-wrap gap-3 border-t border-stone-200 pt-6">
          <Link className="rounded-xl bg-emerald-700 px-4 py-3 font-medium text-white" href="/">우리집으로 돌아가기</Link>
          <Link className="rounded-xl border border-stone-300 px-4 py-3 font-medium text-stone-700" href="/privacy/settings">개인정보 설정</Link>
        </div>
      </article>
    </main>
  );
}

function PolicySection({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="mt-9 leading-7 text-stone-700"><h2 className="mb-3 text-xl font-semibold text-stone-900">{title}</h2>{children}</section>;
}

function PolicyList({ items }: { items: string[] }) {
  return <ul className="list-disc space-y-2 pl-5">{items.map((item) => <li key={item}>{item}</li>)}</ul>;
}
