package com.woorijip.api.household

import org.springframework.data.repository.CrudRepository
import org.springframework.data.jdbc.repository.query.Query

data class HouseholdMember(
    val userId: Long,
    val displayName: String,
)

interface HouseholdMembershipRepository : CrudRepository<HouseholdMembership, Long> {
    fun findAllByHouseholdId(householdId: Long): List<HouseholdMembership>

    fun findAllByUserId(userId: Long): List<HouseholdMembership>

    fun existsByHouseholdIdAndUserId(
        householdId: Long,
        userId: Long,
    ): Boolean

    @Query(
        """
        SELECT users.id AS user_id, users.display_name
        FROM household_memberships
        JOIN users ON users.id = household_memberships.user_id
        WHERE household_memberships.household_id = :householdId
        ORDER BY household_memberships.id
        """,
    )
    fun findMembersByHouseholdId(householdId: Long): List<HouseholdMember>
}
