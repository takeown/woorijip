"use client";

import { AuthenticatedShell } from "../authenticated-shell";
import { SpendingStatisticsPanel } from "../spending-statistics-panel";
import type { StatsUrlState } from "../stats-url-state";

export function StatsPageContent({ initialState }: { initialState: StatsUrlState }) {
  return (
    <AuthenticatedShell>
      {() => <SpendingStatisticsPanel initialState={initialState} refreshKey={0} />}
    </AuthenticatedShell>
  );
}
