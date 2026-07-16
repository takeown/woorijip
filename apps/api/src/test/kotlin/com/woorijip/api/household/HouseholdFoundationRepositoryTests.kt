package com.woorijip.api.household

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
import com.woorijip.api.identity.AuthIdentity
import com.woorijip.api.identity.AuthIdentityRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class HouseholdFoundationRepositoryTests(
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val authIdentityRepository: AuthIdentityRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
) {
    @Test
    fun `saves a user identity household and membership`() {
        val user = appUserRepository.save(appUser("원태"))
        val household = householdRepository.save(household("우리집"))
        val userId = assertNotNull(user.id)
        val householdId = assertNotNull(household.id)

        val identity = authIdentityRepository.save(
            authIdentity(
                userId = userId,
                provider = "google",
                providerSubject = "google-user-1",
                email = "wontae@example.com",
            ),
        )
        val membership = householdMembershipRepository.save(
            membership(householdId, userId),
        )

        assertNotNull(identity.id)
        assertNotNull(membership.id)
        assertEquals(
            identity.id,
            authIdentityRepository.findByProviderAndProviderSubject(
                provider = "google",
                providerSubject = "google-user-1",
            )?.id,
        )
        assertEquals(
            listOf(userId),
            householdMembershipRepository.findAllByHouseholdId(householdId).map(HouseholdMembership::userId),
        )
        assertEquals(
            listOf(householdId),
            householdMembershipRepository.findAllByUserId(userId).map(HouseholdMembership::householdId),
        )
    }

    @Test
    fun `rejects duplicate provider subjects`() {
        val firstUserId = assertNotNull(appUserRepository.save(appUser("원태")).id)
        val secondUserId = assertNotNull(appUserRepository.save(appUser("배우자")).id)
        authIdentityRepository.save(
            authIdentity(firstUserId, "google", "same-subject", "first@example.com"),
        )

        assertFailsWith<DataIntegrityViolationException> {
            authIdentityRepository.save(
                authIdentity(secondUserId, "google", "same-subject", "second@example.com"),
            )
        }
    }

    @Test
    fun `rejects duplicate household memberships`() {
        val userId = assertNotNull(appUserRepository.save(appUser("원태")).id)
        val householdId = assertNotNull(householdRepository.save(household("우리집")).id)
        householdMembershipRepository.save(membership(householdId, userId))

        assertFailsWith<DataIntegrityViolationException> {
            householdMembershipRepository.save(membership(householdId, userId))
        }
    }

    private fun appUser(displayName: String) = AppUser(
        displayName = displayName,
        createdAt = createdAt,
    )

    private fun authIdentity(
        userId: Long,
        provider: String,
        providerSubject: String,
        email: String?,
    ) = AuthIdentity(
        userId = userId,
        provider = provider,
        providerSubject = providerSubject,
        email = email,
        createdAt = createdAt,
    )

    private fun household(name: String) = Household(
        name = name,
        createdAt = createdAt,
    )

    private fun membership(
        householdId: Long,
        userId: Long,
    ) = HouseholdMembership(
        householdId = householdId,
        userId = userId,
        createdAt = createdAt,
    )

    private companion object {
        val createdAt: OffsetDateTime = OffsetDateTime.parse("2026-07-15T12:00:00+09:00")
    }
}
