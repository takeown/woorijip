package com.woorijip.api.household

import com.woorijip.api.auth.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class HouseholdMemberResponse(
    val userId: Long,
    val displayName: String,
)

@RestController
class HouseholdController(
    private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @GetMapping("/households/current/members")
    fun findCurrentHouseholdMembers(
        currentUser: CurrentUser,
    ): List<HouseholdMemberResponse> {
        return householdMembershipRepository
            .findMembersByHouseholdId(currentUser.householdId)
            .map { member -> HouseholdMemberResponse(member.userId, member.displayName) }
    }
}
