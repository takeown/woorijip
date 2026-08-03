package com.woorijip.api.transaction

enum class PaymentMethod {
    CARD,
    CASH,
    QR,
    UNKNOWN,
}

enum class CardIssuer {
    LOTTE,
    BC,
    SAMSUNG,
    SHINHAN,
    WOORI,
    HANA,
    HYUNDAI,
    KB_KOOKMIN,
    NH_NONGHYUP,
}
