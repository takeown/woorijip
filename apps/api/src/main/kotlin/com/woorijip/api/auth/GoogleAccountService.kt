package com.woorijip.api.auth

import com.woorijip.api.household.Household
import com.woorijip.api.household.HouseholdMembership
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.household.HouseholdRepository
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
import com.woorijip.api.identity.AuthIdentity
import com.woorijip.api.identity.AuthIdentityRepository
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
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
) {
    @Transactional
    fun provision(oidcUser: OidcUser): CurrentUser {
        val subject = oidcUser.subject ?: throw accessDenied()
        val email = oidcUser.email?.trim()?.lowercase()
        if (email == null || oidcUser.emailVerified != true || email !in allowedEmails()) {
            throw accessDenied()
        }

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
        val verifiedSubject = subject ?: throw accessDenied()
        val identity = authIdentityRepository.findByProviderAndProviderSubject(GOOGLE, verifiedSubject)
            ?: throw accessDenied()
        return currentUser(identity.userId)
    }

    private fun currentUser(userId: Long): CurrentUser {
        val user = appUserRepository.findById(userId).orElseThrow(::accessDenied)
        val membership = householdMembershipRepository.findAllByUserId(userId).singleOrNull()
            ?: throw accessDenied()
        return CurrentUser(
            id = requireNotNull(user.id),
            displayName = user.displayName,
            householdId = membership.householdId,
        )
    }

    private fun allowedEmails(): Set<String> =
        authProperties.allowedGoogleEmails
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(String::lowercase)
            .toSet()

    private fun accessDenied() = OAuth2AuthenticationException(
        OAuth2Error("access_denied"),
        "허용되지 않은 Google 계정입니다.",
    )

    private companion object {
        const val GOOGLE = "google"
    }
}
