package com.woorijip.api.auth

import com.woorijip.api.household.Household
import com.woorijip.api.household.HouseholdMembership
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.household.HouseholdRepository
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
import com.woorijip.api.identity.AuthIdentity
import com.woorijip.api.identity.AuthIdentityRepository
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class GoogleAccountService(
    private val authProperties: AuthProperties,
    private val appUserRepository: AppUserRepository,
    private val authIdentityRepository: AuthIdentityRepository,
    private val householdRepository: HouseholdRepository,
    private val householdMembershipRepository: HouseholdMembershipRepository,
    private val googleAccountAccessPolicy: GoogleAccountAccessPolicy,
) {
    @Transactional
    fun provision(oidcUser: OidcUser): CurrentUser {
        googleAccountAccessPolicy.requireAllowed(oidcUser)
        val subject = requireNotNull(oidcUser.subject)
        val email = requireNotNull(oidcUser.email).trim().lowercase()

        val existingIdentity = authIdentityRepository.findByProviderAndProviderSubject(GOOGLE, subject)
        if (existingIdentity != null) {
            return currentUser(existingIdentity.userId)
        }

        val now = OffsetDateTime.now()
        val user = appUserRepository.save(
            AppUser(
                displayName = oidcUser.fullName?.takeIf(String::isNotBlank) ?: email,
                createdAt = now,
            ),
        )
        val userId = requireNotNull(user.id)
        authIdentityRepository.save(
            AuthIdentity(
                userId = userId,
                provider = GOOGLE,
                providerSubject = subject,
                email = email,
                createdAt = now,
            ),
        )

        val household = householdRepository.findFirstByOrderByIdAsc()
            ?: householdRepository.save(
                Household(
                    name = authProperties.bootstrapHouseholdName,
                    createdAt = now,
                ),
            )
        val householdId = requireNotNull(household.id)
        householdMembershipRepository.save(
            HouseholdMembership(
                householdId = householdId,
                userId = userId,
                createdAt = now,
            ),
        )

        return CurrentUser(userId, user.displayName, householdId)
    }

    @Transactional(readOnly = true)
    fun findByGoogleSubject(subject: String?): CurrentUser {
        val verifiedSubject = subject ?: throw accountNotAllowed()
        val identity = authIdentityRepository.findByProviderAndProviderSubject(GOOGLE, verifiedSubject)
            ?: throw accountNotAllowed()
        return currentUser(identity.userId)
    }

    private fun currentUser(userId: Long): CurrentUser {
        val user = appUserRepository.findById(userId).orElseThrow(::accountNotAllowed)
        val membership = householdMembershipRepository.findAllByUserId(userId).singleOrNull()
            ?: throw accountNotAllowed()
        return CurrentUser(
            id = requireNotNull(user.id),
            displayName = user.displayName,
            householdId = membership.householdId,
        )
    }

    private companion object {
        const val GOOGLE = "google"
    }
}
