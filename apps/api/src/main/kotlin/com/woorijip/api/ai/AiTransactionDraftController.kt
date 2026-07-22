package com.woorijip.api.ai

import com.woorijip.api.auth.GoogleAccountService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class CreateAiTransactionDraftRequest(
    @field:NotEmpty
    @field:Size(max = 3)
    val messages: List<String>?,
)

@RestController
class AiTransactionDraftController(
    private val googleAccountService: GoogleAccountService,
    private val aiTransactionDraftService: AiTransactionDraftService,
) {
    @PostMapping("/ai/transaction-drafts")
    fun create(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @Valid @RequestBody request: CreateAiTransactionDraftRequest,
    ): AiTransactionDraft {
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
        val messages = requireNotNull(request.messages).map(String::trim)
        if (messages.any { message -> message.isBlank() || message.length > 500 }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "각 거래 입력은 1자 이상 500자 이하여야 합니다.")
        }
        return aiTransactionDraftService.create(currentUser, messages)
    }
}
