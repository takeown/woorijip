package com.woorijip.api.ai

import com.woorijip.api.auth.GoogleAccountService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CreateAiTransactionDraftRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val message: String?,
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
        return aiTransactionDraftService.create(currentUser, requireNotNull(request.message).trim())
    }
}
