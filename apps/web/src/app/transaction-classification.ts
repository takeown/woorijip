export type TransactionCategory =
  | "FOOD"
  | "HOUSING"
  | "TRANSPORT"
  | "LIVING"
  | "HEALTH"
  | "LEISURE"
  | "EDUCATION"
  | "FINANCE_INSURANCE"
  | "FAMILY_EVENT"
  | "OTHER";

export type TransactionTag =
  | "SUBSCRIPTION"
  | "UTILITY"
  | "RECURRING_PAYMENT";

export const transactionCategories: {
  value: TransactionCategory;
  label: string;
  examples: string[];
}[] = [
  { value: "FOOD", label: "식비", examples: ["장보기", "외식", "배달음식", "카페", "간식", "술", "회사 점심", "편의점 식품", "식재료", "음료"] },
  { value: "HOUSING", label: "주거", examples: ["월세", "대출 이자", "관리비", "전기요금", "수도요금", "가스요금", "주택 수리", "가구", "이사", "청소 서비스"] },
  { value: "TRANSPORT", label: "교통", examples: ["지하철", "버스", "택시", "주유", "주차", "통행료", "자동차 정비", "기차", "렌터카", "교통카드"] },
  { value: "LIVING", label: "생활", examples: ["세제", "휴지", "주방용품", "의류", "신발", "미용실", "화장품", "세탁", "택배", "생활가전"] },
  { value: "HEALTH", label: "건강", examples: ["병원", "치과", "약국", "건강검진", "영양제", "안경", "물리치료", "심리상담", "운동 치료", "의료기기"] },
  { value: "LEISURE", label: "여가", examples: ["영화", "공연", "여행", "게임", "취미용품", "도서", "음원", "OTT", "스포츠 관람", "놀이공원"] },
  { value: "EDUCATION", label: "교육", examples: ["학원", "온라인 강의", "자격증", "교재", "외국어 수업", "직무 교육", "세미나", "독서 모임", "과외", "교육 소프트웨어"] },
  { value: "FINANCE_INSURANCE", label: "금융·보험", examples: ["생명보험", "실손보험", "화재보험", "카드 연회비", "계좌 수수료", "대출 이자", "증권 수수료", "환전 수수료", "세금", "연금"] },
  { value: "FAMILY_EVENT", label: "경조사", examples: ["축의금", "조의금", "생일 선물", "명절 선물", "부모님 용돈", "기념일 선물", "출산 선물", "돌잔치", "집들이 선물", "모임 회비"] },
  { value: "OTHER", label: "기타", examples: ["과태료", "알 수 없는 결제", "행정 비용", "기부금", "후원금", "반려동물", "종교 활동", "업무 선지출", "분실 보상", "미분류 지출"] },
];

export const transactionTags: {
  value: TransactionTag;
  label: string;
}[] = [
  { value: "SUBSCRIPTION", label: "구독" },
  { value: "UTILITY", label: "공과금" },
  { value: "RECURRING_PAYMENT", label: "정기결제" },
];

export function categoryLabel(category: TransactionCategory) {
  return transactionCategories.find((item) => item.value === category)?.label ?? category;
}

export function tagLabel(tag: TransactionTag) {
  return transactionTags.find((item) => item.value === tag)?.label ?? tag;
}
