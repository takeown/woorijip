package com.woorijip.api.household

import org.springframework.data.repository.CrudRepository

interface HouseholdMembershipRepository : CrudRepository<HouseholdMembership, Long> {
    fun findAllByHouseholdId(householdId: Long): List<HouseholdMembership>

    fun findAllByUserId(userId: Long): List<HouseholdMembership>
}
