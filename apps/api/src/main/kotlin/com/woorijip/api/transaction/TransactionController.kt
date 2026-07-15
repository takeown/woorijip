package com.woorijip.api.transaction

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

data class CreateTransactionRequest(
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
    val merchant: String,
    val amount: Long,
    val category: String,
    val occurredAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
)

@RestController
class TransactionController(
    private val transactionRepository: TransactionRepository,
) {
    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateTransactionRequest,
    ): TransactionResponse {
        val transaction =
            Transaction(
                merchant = requireNotNull(request.merchant).trim(),
                amount = requireNotNull(request.amount),
                category = requireNotNull(request.category).trim(),
                occurredAt = requireNotNull(request.occurredAt),
                createdAt = OffsetDateTime.now(),
            )

        return transactionRepository.save(transaction).toResponse()
    }

    @GetMapping("/transactions")
    fun findAll(): List<TransactionResponse> =
        transactionRepository
            .findAllByOrderByOccurredAtDescIdDesc()
            .map(Transaction::toResponse)
}

private fun Transaction.toResponse(): TransactionResponse =
    TransactionResponse(
        id = requireNotNull(id),
        merchant = merchant,
        amount = amount,
        category = category,
        occurredAt = occurredAt,
        createdAt = createdAt,
    )
