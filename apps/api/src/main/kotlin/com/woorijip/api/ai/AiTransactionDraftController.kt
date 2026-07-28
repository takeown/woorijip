package com.woorijip.api.ai

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CreateAiTransactionDraftRequest(
    @field:NotEmpty
    @field:Size(max = 3)
    val messages: List<String>?,
)

@RestController
class AiTransactionDraftController(
    private val aiTransactionDraftService: AiTransactionDraftService,
) {
    @PostMapping("/ai/transaction-drafts")
    fun create(
        currentUser: CurrentUser,
        @Valid @RequestBody request: CreateAiTransactionDraftRequest,
    ): AiTransactionDraft {
        val messages = requireNotNull(request.messages).map(String::trim)
        if (messages.any { message -> message.isBlank() || message.length > 500 }) {
            throw ApiException(ErrorCode.INVALID_AI_MESSAGE, "각 거래 입력은 1자 이상 500자 이하여야 합니다.")
        }
        return aiTransactionDraftService.create(currentUser, messages)
    }
}
