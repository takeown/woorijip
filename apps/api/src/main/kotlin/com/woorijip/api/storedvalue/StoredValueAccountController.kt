package com.woorijip.api.storedvalue

import com.woorijip.api.auth.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

data class StoredValueAccountResponse(
    val id: Long,
    val ownerUserId: Long,
    val ownerDisplayName: String,
    val category: StoredValueAccountCategory,
    val customCategoryName: String?,
    val automationKey: StoredValueAutomationKey?,
    val name: String,
    val balance: Long,
    val archived: Boolean,
    val canDelete: Boolean,
)

data class CreateStoredValueAccountRequest(
    @field:NotNull
    @field:Positive
    val ownerUserId: Long?,
    @field:NotBlank
    @field:Size(max = 100)
    val name: String?,
    @field:NotNull
    val category: StoredValueAccountCategory?,
    @field:Size(max = 40)
    val customCategoryName: String?,
    val automationKey: StoredValueAutomationKey?,
)

data class UpdateStoredValueAccountRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String?,
    @field:NotNull
    val category: StoredValueAccountCategory?,
    @field:Size(max = 40)
    val customCategoryName: String?,
    @field:NotNull
    val archived: Boolean?,
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

data class AdjustStoredValueAccountRequest(
    @field:NotNull
    val direction: StoredValueAdjustmentDirection?,
    @field:NotNull
    @field:Positive
    val amount: Long?,
    @field:NotBlank
    @field:Size(max = 100)
    val reason: String?,
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

    @PostMapping("/stored-value-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        currentUser: CurrentUser,
        @Valid @RequestBody request: CreateStoredValueAccountRequest,
    ): StoredValueAccountResponse =
        service.create(
            currentUser = currentUser,
            ownerUserId = requireNotNull(request.ownerUserId),
            name = requireNotNull(request.name).trim(),
            category = requireNotNull(request.category),
            customCategoryName = request.customCategoryName?.trim()?.takeIf(String::isNotEmpty),
            automationKey = request.automationKey,
        ).toResponse()

    @PatchMapping("/stored-value-accounts/{accountId}")
    fun update(
        currentUser: CurrentUser,
        @PathVariable accountId: Long,
        @Valid @RequestBody request: UpdateStoredValueAccountRequest,
    ): StoredValueAccountResponse =
        service.update(
            currentUser = currentUser,
            accountId = accountId,
            name = requireNotNull(request.name).trim(),
            category = requireNotNull(request.category),
            customCategoryName = request.customCategoryName?.trim()?.takeIf(String::isNotEmpty),
            archived = requireNotNull(request.archived),
        ).toResponse()

    @DeleteMapping("/stored-value-accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(currentUser: CurrentUser, @PathVariable accountId: Long) {
        service.delete(currentUser, accountId)
    }

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

    @PostMapping("/stored-value-accounts/{accountId}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    fun adjust(
        currentUser: CurrentUser,
        @PathVariable accountId: Long,
        @Valid @RequestBody request: AdjustStoredValueAccountRequest,
    ): StoredValueAccountResponse =
        service.adjust(
            currentUser = currentUser,
            accountId = accountId,
            direction = requireNotNull(request.direction),
            amount = requireNotNull(request.amount),
            reason = requireNotNull(request.reason).trim(),
            occurredAt = requireNotNull(request.occurredAt),
        ).toResponse()
}

private fun StoredValueAccount.toResponse(): StoredValueAccountResponse =
    StoredValueAccountResponse(
        id = id,
        ownerUserId = ownerUserId,
        ownerDisplayName = ownerDisplayName,
        category = category,
        customCategoryName = customCategoryName,
        automationKey = automationKey,
        name = name,
        balance = balance,
        archived = archivedAt != null,
        canDelete = canDelete,
    )
