package com.woorijip.api.error

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILTER(HttpStatus.BAD_REQUEST),
    PAYER_NOT_IN_HOUSEHOLD(HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_DETAILS(HttpStatus.BAD_REQUEST),
    INVALID_AI_MESSAGE(HttpStatus.BAD_REQUEST),
    INVALID_CARD_STATEMENT(HttpStatus.BAD_REQUEST),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    CARD_STATEMENT_IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSACTION_MODIFIED(HttpStatus.CONFLICT),
    AI_DRAFT_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
}
