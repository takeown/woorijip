package com.woorijip.api.error

import com.woorijip.api.statement.InvalidCardStatementException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * 모든 JSON 오류 응답을 RFC 9457 ProblemDetail 하나로 통일하고 `code`를 덧붙인다.
 * 이 advice가 ResponseEntityExceptionHandler를 상속하므로 Spring Boot의
 * ProblemDetailsExceptionHandler는 물러나고 프레임워크 예외도 여기로 들어온다.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException): ProblemDetail =
        problemDetail(exception.code, exception.message)

    @ExceptionHandler(InvalidCardStatementException::class)
    fun handleInvalidCardStatement(exception: InvalidCardStatementException): ProblemDetail =
        problemDetail(
            ErrorCode.INVALID_CARD_STATEMENT,
            exception.message ?: "명세서를 처리할 수 없습니다.",
        )

    override fun handleExceptionInternal(
        exception: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(exception, body, headers, statusCode, request)
        val problemDetail = response?.body as? ProblemDetail ?: return response
        if (statusCode.is4xxClientError) {
            problemDetail.setProperty(CODE_PROPERTY, ErrorCode.INVALID_REQUEST.name)
        }
        return response
    }

    private fun problemDetail(
        code: ErrorCode,
        message: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(code.status, message).apply {
            setProperty(CODE_PROPERTY, code.name)
        }

    private companion object {
        const val CODE_PROPERTY = "code"
    }
}
