package com.woorijip.api.transaction

import com.woorijip.api.auth.GoogleAccountService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

data class CreateTransactionRequest(
    @field:NotNull
    @field:Positive
    val payerId: Long?,
    @field:NotBlank
    @field:Size(max = 200)
    val merchant: String?,
    @field:NotNull
    @field:Positive
    val amount: Long?,
    @field:NotBlank
    @field:Size(max = 100)
    val category: String?,
    @field:NotNull
    val occurredAt: OffsetDateTime?,
)

data class TransactionResponse(
    val id: Long,
    val payerId: Long,
    val merchant: String,
    val amount: Long,
    val category: String,
    val occurredAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
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
                amount = requireNotNull(request.amount),
                category = requireNotNull(request.category).trim(),
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
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 결제자 필터입니다.")
        }
        return transactionService
            .findAll(currentUser, payerFilter)
            .map(Transaction::toResponse)
    }
}

private fun Transaction.toResponse(): TransactionResponse =
    TransactionResponse(
        id = requireNotNull(id),
        payerId = payerId,
        merchant = merchant,
        amount = amount,
        category = category,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )
