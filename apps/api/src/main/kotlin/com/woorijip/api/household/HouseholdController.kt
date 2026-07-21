package com.woorijip.api.household

import com.woorijip.api.auth.GoogleAccountService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class HouseholdMemberResponse(
    val userId: Long,
    val displayName: String,
)

@RestController
class HouseholdController(
    private val googleAccountService: GoogleAccountService,
    private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @GetMapping("/households/current/members")
    fun findCurrentHouseholdMembers(
        @AuthenticationPrincipal oidcUser: OidcUser,
    ): List<HouseholdMemberResponse> {
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
        return householdMembershipRepository
            .findMembersByHouseholdId(currentUser.householdId)
            .map { member -> HouseholdMemberResponse(member.userId, member.displayName) }
    }
}
