"use client";

import { AuthenticatedShell } from "../authenticated-shell";
import { SpendingStatisticsPanel } from "../spending-statistics-panel";

export default function StatsPage() {
  return (
    <AuthenticatedShell>
      {() => <SpendingStatisticsPanel refreshKey={0} />}
    </AuthenticatedShell>
  );
}
