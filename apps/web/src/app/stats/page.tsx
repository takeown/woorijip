import { normalizeStatsUrlState, todayInSeoul } from "../stats-url-state";
import { StatsPageContent } from "./stats-page-content";

export default async function StatsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const initialState = normalizeStatsUrlState(await searchParams, todayInSeoul());
  return <StatsPageContent initialState={initialState} />;
}
