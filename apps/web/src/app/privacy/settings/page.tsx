"use client";

import { useState } from "react";
import { AuthenticatedShell, type PrivacyConsentStatus } from "../../authenticated-shell";
import { PrivacyConsentForm } from "../../privacy-consent-form";

export default function PrivacySettingsPage() {
  return (
    <AuthenticatedShell>
      {(user) => <PrivacySettings initialStatus={user.privacyConsent} />}
    </AuthenticatedShell>
  );
}

function PrivacySettings({ initialStatus }: { initialStatus: PrivacyConsentStatus }) {
  const [status, setStatus] = useState(initialStatus);
  const [saved, setSaved] = useState(false);
  return (
    <section className="mx-auto max-w-2xl rounded-3xl border border-stone-200 bg-stone-50 p-5 sm:p-8">
      <p className="text-sm font-medium text-emerald-700">개인정보</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">동의 설정</h1>
      <p className="mt-3 text-sm leading-6 text-stone-600">OpenAI 국외 이전 동의는 언제든 변경할 수 있습니다.</p>
      {saved ? <p className="mt-5 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800" role="status">동의 설정을 저장했습니다.</p> : null}
      <div className="mt-6">
        <PrivacyConsentForm initialStatus={status} onSaved={(next) => { setStatus(next); setSaved(true); }} settings />
      </div>
    </section>
  );
}
