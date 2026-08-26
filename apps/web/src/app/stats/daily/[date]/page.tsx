import { notFound } from "next/navigation";
import {
  DailySpendingDetailsPage,
  type SpendingPayer,
} from "../../../daily-spending-details-panel";

export default async function DailySpendingPage({
  params,
  searchParams,
}: {
  params: Promise<{ date: string }>;
  searchParams: Promise<{ payer?: string | string[] }>;
}) {
  const { date } = await params;
  if (!isValidDate(date)) notFound();
  const payer = normalizePayer((await searchParams).payer);

  return <DailySpendingDetailsPage date={date} payer={payer} />;
}

function normalizePayer(value: string | string[] | undefined): SpendingPayer {
  const payer = Array.isArray(value) ? value[0] : value;
  return payer === "ME" || payer === "PARTNER" ? payer : "ALL";
}

function isValidDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
