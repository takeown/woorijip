package com.woorijip.api.transaction

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Base64

data class CreateTransactionRequest(
    @field:NotNull
    @field:Positive
    val payerId: Long?,
    @field:NotBlank
    @field:Size(max = 200)
    val merchant: String?,
    @field:Size(max = 500)
    val description: String?,
    @field:NotNull
    @field:Positive
    val amount: Long?,
    @field:NotNull
    val category: TransactionCategory?,
    @field:Size(max = 3)
    val tags: Set<TransactionTag> = emptySet(),
    val classificationSource: ClassificationSource = ClassificationSource.USER,
    @field:Positive
    val classificationRuleId: Long? = null,
    val saveMerchantRule: Boolean = false,
    @field:NotNull
    val paymentMethod: PaymentMethod?,
    val cardIssuer: CardIssuer?,
    @field:Positive
    val storedValueAccountId: Long? = null,
    @field:NotNull
    val occurredAt: OffsetDateTime?,
)

data class TransactionResponse(
    val id: Long,
    val payerId: Long,
    val merchant: String,
    val description: String?,
    val amount: Long,
    val category: TransactionCategory,
    val tags: Set<TransactionTag>,
    val classificationSource: ClassificationSource,
    val classificationConfidence: ClassificationConfidence,
    val classificationConfirmedAt: OffsetDateTime?,
    val paymentMethod: PaymentMethod,
    val cardIssuer: CardIssuer?,
    val storedValueAccountId: Long?,
    val occurredAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class TransactionPageResponse(
    val items: List<TransactionResponse>,
    val nextCursor: String?,
)

data class UpdateTransactionRequest(
    @field:NotNull
    val expectedUpdatedAt: OffsetDateTime?,
    @field:NotNull
    @field:Positive
    val payerId: Long?,
    @field:NotBlank
    @field:Size(max = 200)
    val merchant: String?,
    @field:Size(max = 500)
    val description: String?,
    @field:NotNull
    @field:Positive
    val amount: Long?,
    @field:NotNull
    val category: TransactionCategory?,
    @field:Size(max = 3)
    val tags: Set<TransactionTag> = emptySet(),
    @field:NotNull
    val paymentMethod: PaymentMethod?,
    val cardIssuer: CardIssuer?,
    @field:Positive
    val storedValueAccountId: Long? = null,
    @field:NotNull
    val occurredAt: OffsetDateTime?,
)

data class DeleteTransactionRequest(
    @field:NotNull
    val expectedUpdatedAt: OffsetDateTime?,
)

@RestController
class TransactionController(
    private val transactionService: TransactionService,
) {
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        currentUser: CurrentUser,
        @Valid @RequestBody request: CreateTransactionRequest,
    ): TransactionResponse {
        return transactionService
            .create(
                currentUser,
                TransactionDraft(
                    payerId = requireNotNull(request.payerId),
                    merchant = requireNotNull(request.merchant).trim(),
                    description = request.description?.trim()?.takeIf(String::isNotEmpty),
                    amount = requireNotNull(request.amount),
                    category = requireNotNull(request.category),
                    tags = request.tags,
                    classificationSource = request.classificationSource,
                    paymentMethod = requireNotNull(request.paymentMethod),
                    cardIssuer = request.cardIssuer,
                    storedValueAccountId = request.storedValueAccountId,
                    occurredAt = requireNotNull(request.occurredAt),
                ),
                classificationRuleId = request.classificationRuleId,
                saveMerchantRule = request.saveMerchantRule,
            ).toResponse()
    }

    @GetMapping("/transactions")
    fun findPage(
        currentUser: CurrentUser,
        @RequestParam(defaultValue = "all") payer: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): TransactionPageResponse {
        if (size !in 1..100) {
            throw ApiException(ErrorCode.INVALID_REQUEST, "페이지 크기는 1건부터 100건까지 지정할 수 있습니다.")
        }
        val payerFilter = try {
            PayerFilter.valueOf(payer.uppercase())
        } catch (_: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNSUPPORTED_FILTER, "지원하지 않는 결제자 필터입니다.")
        }
        val query = q?.trim()?.takeIf(String::isNotEmpty)
        if (query != null && query.length > 100) {
            throw ApiException(ErrorCode.INVALID_REQUEST, "검색어는 100자 이하로 입력해 주세요.")
        }
        if (from != null && to != null && from > to) {
            throw ApiException(ErrorCode.INVALID_REQUEST, "시작 날짜는 종료 날짜보다 늦을 수 없습니다.")
        }
        val page = transactionService.findPage(
            currentUser = currentUser,
            filters = TransactionFilters(
                payer = payerFilter,
                query = query,
                fromDate = from,
                toDate = to,
            ),
            cursor = decodeCursor(cursor),
            size = size,
        )
        return TransactionPageResponse(
            items = page.items.map(Transaction::toResponse),
            nextCursor = page.nextCursor?.encode(),
        )
    }

    @PutMapping("/transactions/{transactionId}")
    fun update(
        currentUser: CurrentUser,
        @PathVariable transactionId: Long,
        @Valid @RequestBody request: UpdateTransactionRequest,
    ): TransactionResponse {
        return transactionService
            .update(
                currentUser = currentUser,
                transactionId = transactionId,
                expectedUpdatedAt = requireNotNull(request.expectedUpdatedAt),
                draft = TransactionDraft(
                    payerId = requireNotNull(request.payerId),
                    merchant = requireNotNull(request.merchant).trim(),
                    description = request.description?.trim()?.takeIf(String::isNotEmpty),
                    amount = requireNotNull(request.amount),
                    category = requireNotNull(request.category),
                    tags = request.tags,
                    classificationSource = ClassificationSource.USER,
                    paymentMethod = requireNotNull(request.paymentMethod),
                    cardIssuer = request.cardIssuer,
                    storedValueAccountId = request.storedValueAccountId,
                    occurredAt = requireNotNull(request.occurredAt),
                ),
            ).toResponse()
    }

    @DeleteMapping("/transactions/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        currentUser: CurrentUser,
        @PathVariable transactionId: Long,
        @Valid @RequestBody request: DeleteTransactionRequest,
    ) {
        transactionService.delete(
            currentUser = currentUser,
            transactionId = transactionId,
            expectedUpdatedAt = requireNotNull(request.expectedUpdatedAt),
        )
    }
}

private fun decodeCursor(value: String?): TransactionCursor? {
    if (value == null) return null
    return runCatching {
        val decoded = String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8,
        )
        val parts = decoded.split('|', limit = 2)
        require(parts.size == 2)
        TransactionCursor(
            occurredAt = OffsetDateTime.parse(parts[0]),
            id = parts[1].toLong().also { require(it > 0) },
        )
    }.getOrElse {
        throw ApiException(ErrorCode.INVALID_REQUEST, "올바르지 않은 거래 조회 커서입니다.")
    }
}

private fun TransactionCursor.encode(): String =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString("$occurredAt|$id".toByteArray(StandardCharsets.UTF_8))

private fun Transaction.toResponse(): TransactionResponse =
    TransactionResponse(
        id = requireNotNull(id),
        payerId = payerId,
        merchant = merchant,
        description = description,
        amount = amount,
        category = category,
        tags = tags,
        classificationSource = classificationSource,
        classificationConfidence = classificationConfidence,
        classificationConfirmedAt = classificationConfirmedAt,
        paymentMethod = paymentMethod,
        cardIssuer = cardIssuer,
        storedValueAccountId = storedValueAccountId,
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
