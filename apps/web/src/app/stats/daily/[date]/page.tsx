import { notFound } from "next/navigation";
import { DailySpendingDetailsPage } from "../../../daily-spending-details-panel";
import { isDateValue, type SpendingPayer } from "../../../stats-url-state";

export default async function DailySpendingPage({
  params,
  searchParams,
}: {
  params: Promise<{ date: string }>;
  searchParams: Promise<{
    payer?: string | string[];
    statsDate?: string | string[];
  }>;
}) {
  const { date } = await params;
  if (!isDateValue(date)) notFound();
  const query = await searchParams;
  const payer = normalizePayer(query.payer);
  const statsDateValue = firstValue(query.statsDate);
  const statsDate = statsDateValue && isDateValue(statsDateValue) ? statsDateValue : date;

  return <DailySpendingDetailsPage date={date} payer={payer} statsDate={statsDate} />;
}

function normalizePayer(value: string | string[] | undefined): SpendingPayer {
  const payer = Array.isArray(value) ? value[0] : value;
  return payer === "ME" || payer === "PARTNER" ? payer : "ALL";
}

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
