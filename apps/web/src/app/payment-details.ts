export type PaymentMethod = "CARD" | "CASH";
export type StoredPaymentMethod = PaymentMethod | "UNKNOWN";

export type CardIssuer =
  | "LOTTE"
  | "BC"
  | "SAMSUNG"
  | "SHINHAN"
  | "WOORI"
  | "HANA"
  | "HYUNDAI"
  | "KB_KOOKMIN"
  | "NH_NONGHYUP";

export const cardIssuers: { value: CardIssuer; label: string }[] = [
  { value: "LOTTE", label: "롯데카드" },
  { value: "BC", label: "BC카드" },
  { value: "SAMSUNG", label: "삼성카드" },
  { value: "SHINHAN", label: "신한카드" },
  { value: "WOORI", label: "우리카드" },
  { value: "HANA", label: "하나카드" },
  { value: "HYUNDAI", label: "현대카드" },
  { value: "KB_KOOKMIN", label: "KB국민카드" },
  { value: "NH_NONGHYUP", label: "NH농협카드" },
];

export function paymentDetailsLabel(
  paymentMethod: StoredPaymentMethod,
  cardIssuer: CardIssuer | null,
) {
  if (paymentMethod === "CASH") return "현금";
  if (paymentMethod === "UNKNOWN") return "결제수단 미지정";
  return cardIssuers.find((issuer) => issuer.value === cardIssuer)?.label ?? "카드";
}
