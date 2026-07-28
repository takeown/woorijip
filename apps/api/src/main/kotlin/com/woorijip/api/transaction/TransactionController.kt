package com.woorijip.api.transaction

import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

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
    @field:NotNull
    val paymentMethod: PaymentMethod?,
    val cardIssuer: CardIssuer?,
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
    val occurredAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
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
    @field:NotNull
    val occurredAt: OffsetDateTime?,
)

data class DeleteTransactionRequest(
    @field:NotNull
    val expectedUpdatedAt: OffsetDateTime?,
)

@RestController
class TransactionController(
    private val googleAccountService: GoogleAccountService,
    private val transactionService: TransactionService,
) {
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @Valid @RequestBody request: CreateTransactionRequest,
    ): TransactionResponse {
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
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
                    occurredAt = requireNotNull(request.occurredAt),
                ),
            ).toResponse()
    }

    @GetMapping("/transactions")
    fun findAll(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @RequestParam(defaultValue = "all") payer: String,
    ): List<TransactionResponse> {
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
        val payerFilter = try {
            PayerFilter.valueOf(payer.uppercase())
        } catch (_: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNSUPPORTED_FILTER, "지원하지 않는 결제자 필터입니다.")
        }
        return transactionService
            .findAll(currentUser, payerFilter)
            .map(Transaction::toResponse)
    }

    @PutMapping("/transactions/{transactionId}")
    fun update(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @PathVariable transactionId: Long,
        @Valid @RequestBody request: UpdateTransactionRequest,
    ): TransactionResponse {
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
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
                    occurredAt = requireNotNull(request.occurredAt),
                ),
            ).toResponse()
    }

    @DeleteMapping("/transactions/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @PathVariable transactionId: Long,
        @Valid @RequestBody request: DeleteTransactionRequest,
    ) {
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
        transactionService.delete(
            currentUser = currentUser,
            transactionId = transactionId,
            expectedUpdatedAt = requireNotNull(request.expectedUpdatedAt),
        )
    }
}

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
        occurredAt = occurredAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
