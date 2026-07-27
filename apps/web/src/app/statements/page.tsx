"use client";

import { AuthenticatedShell } from "../authenticated-shell";
import { CardStatementPanel } from "../card-statement-panel";

export default function StatementsPage() {
  return (
    <AuthenticatedShell>
      {() => <CardStatementPanel />}
    </AuthenticatedShell>
  );
}
