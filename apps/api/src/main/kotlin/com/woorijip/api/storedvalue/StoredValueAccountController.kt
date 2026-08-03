package com.woorijip.api.storedvalue

import com.woorijip.api.auth.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

data class StoredValueAccountResponse(
    val id: Long,
    val type: StoredValueAccountType,
    val name: String,
    val balance: Long,
)

data class CreditStoredValueAccountRequest(
    @field:NotNull
    @field:Positive
    val balanceAmount: Long?,
    @field:NotNull
    @field:PositiveOrZero
    val paidAmount: Long?,
    @field:Size(max = 100)
    val sourceName: String?,
    @field:NotNull
    val occurredAt: OffsetDateTime?,
)

@RestController
class StoredValueAccountController(
    private val service: StoredValueAccountService,
) {
    @GetMapping("/stored-value-accounts")
    fun findAll(currentUser: CurrentUser): List<StoredValueAccountResponse> =
        service.findAll(currentUser).map(StoredValueAccount::toResponse)

    @PostMapping("/stored-value-accounts/{accountId}/credits")
    @ResponseStatus(HttpStatus.CREATED)
    fun credit(
        currentUser: CurrentUser,
        @PathVariable accountId: Long,
        @Valid @RequestBody request: CreditStoredValueAccountRequest,
    ): StoredValueAccountResponse =
        service.credit(
            currentUser = currentUser,
            accountId = accountId,
            balanceAmount = requireNotNull(request.balanceAmount),
            paidAmount = requireNotNull(request.paidAmount),
            sourceName = request.sourceName?.trim()?.takeIf(String::isNotEmpty),
            occurredAt = requireNotNull(request.occurredAt),
        ).toResponse()
}

private fun StoredValueAccount.toResponse(): StoredValueAccountResponse =
    StoredValueAccountResponse(id, type, name, balance)
