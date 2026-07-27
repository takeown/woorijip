package com.woorijip.api.transaction

enum class TransactionCategory(
    val label: String,
) {
    FOOD("식비"),
    HOUSING("주거"),
    TRANSPORT("교통"),
    LIVING("생활"),
    HEALTH("건강"),
    LEISURE("여가"),
    EDUCATION("교육"),
    FINANCE_INSURANCE("금융·보험"),
    FAMILY_EVENT("경조사"),
    OTHER("기타"),
}

enum class TransactionTag {
    SUBSCRIPTION,
    UTILITY,
    RECURRING_PAYMENT,
}

enum class ClassificationSource {
    USER,
    MERCHANT_RULE,
    HISTORY,
    AI,
    MIGRATION,
}

enum class ClassificationConfidence {
    HIGH,
    MEDIUM,
    LOW,
}
