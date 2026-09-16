"use client";

import { AuthenticatedShell } from "../authenticated-shell";
import { CapturePanel } from "../capture-panel";
import { AiConsentNotice } from "../ai-consent-notice";

export default function CapturesPage() {
  return (
    <AuthenticatedShell>
      {(user) => user.privacyConsent.aiOverseasTransferAgreed
        ? <CapturePanel currentUserId={user.id} />
        : <section className="mx-auto max-w-2xl"><h1 className="mb-5 text-3xl font-semibold">카드 내역 캡처</h1><AiConsentNotice /></section>}
    </AuthenticatedShell>
  );
}
