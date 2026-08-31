export type SpendingPeriod = "DAY" | "WEEK" | "MONTH";
export type SpendingPayer = "ALL" | "ME" | "PARTNER";

export type StatsUrlState = {
  period: SpendingPeriod;
  payer: SpendingPayer;
  referenceDate: string;
  calendarExpanded: boolean;
};

type SearchParams = Record<string, string | string[] | undefined>;

export function normalizeStatsUrlState(
  searchParams: SearchParams,
  fallbackDate: string,
): StatsUrlState {
  const period = firstValue(searchParams.period)?.toUpperCase();
  const payer = firstValue(searchParams.payer)?.toUpperCase();
  const referenceDate = firstValue(searchParams.date);

  return {
    period: period === "DAY" || period === "WEEK" || period === "MONTH" ? period : "MONTH",
    payer: payer === "ME" || payer === "PARTNER" ? payer : "ALL",
    referenceDate: referenceDate && isDateValue(referenceDate) ? referenceDate : fallbackDate,
    calendarExpanded: firstValue(searchParams.calendar) === "open",
  };
}

export function statsUrl(state: StatsUrlState): string {
  const searchParams = new URLSearchParams({
    period: state.period,
    payer: state.payer,
    date: state.referenceDate,
  });
  if (state.calendarExpanded) searchParams.set("calendar", "open");
  return `/stats?${searchParams.toString()}`;
}

export function dailyStatsUrl(
  date: string,
  payer: SpendingPayer,
  statsDate: string,
): string {
  const searchParams = new URLSearchParams({ payer, statsDate });
  return `/stats/daily/${date}?${searchParams.toString()}`;
}

export function calendarReturnUrl(payer: SpendingPayer, statsDate: string): string {
  return statsUrl({
    period: "MONTH",
    payer,
    referenceDate: statsDate,
    calendarExpanded: true,
  });
}

export function isDateValue(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function todayInSeoul(now = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", {
    day: "2-digit",
    month: "2-digit",
    timeZone: "Asia/Seoul",
    year: "numeric",
  }).format(now);
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
