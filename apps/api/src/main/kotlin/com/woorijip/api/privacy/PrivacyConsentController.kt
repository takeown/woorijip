package com.woorijip.api.privacy

import com.woorijip.api.auth.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PrivacyConsentUpdateRequest(
    @field:AssertTrue(message = "개인정보 처리방침 동의가 필요합니다.")
    val privacyPolicyAgreed: Boolean,
    val aiOverseasTransferAgreed: Boolean,
)

@RestController
@RequestMapping("/auth/privacy-consents")
class PrivacyConsentController(
    private val service: PrivacyConsentService,
) {
    @GetMapping
    fun status(currentUser: CurrentUser): PrivacyConsentStatus = service.status(currentUser.id)

    @PutMapping
    fun update(
        currentUser: CurrentUser,
        @Valid @RequestBody request: PrivacyConsentUpdateRequest,
    ): PrivacyConsentStatus = service.update(
        currentUser.id,
        request.privacyPolicyAgreed,
        request.aiOverseasTransferAgreed,
    )
}
