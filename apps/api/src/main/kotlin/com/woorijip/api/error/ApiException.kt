package com.woorijip.api.error

class ApiException(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)
