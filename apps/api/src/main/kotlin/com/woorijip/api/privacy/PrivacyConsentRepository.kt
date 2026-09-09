package com.woorijip.api.privacy

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

enum class PrivacyConsentType {
    PRIVACY_POLICY,
    AI_OVERSEAS_TRANSFER,
}

data class PrivacyConsentEvent(
    val policyVersion: String,
    val agreed: Boolean,
    val createdAt: OffsetDateTime,
)

@Repository
class PrivacyConsentRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun latest(userId: Long, type: PrivacyConsentType): PrivacyConsentEvent? =
        jdbc.query(
            """
            SELECT policy_version, agreed, created_at
            FROM privacy_consent_events
            WHERE user_id = :userId AND consent_type = :type
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            mapOf("userId" to userId, "type" to type.name),
        ) { row, _ ->
            PrivacyConsentEvent(
                policyVersion = row.getString("policy_version"),
                agreed = row.getBoolean("agreed"),
                createdAt = row.getObject("created_at", OffsetDateTime::class.java),
            )
        }.firstOrNull()

    fun record(userId: Long, type: PrivacyConsentType, policyVersion: String, agreed: Boolean) {
        jdbc.update(
            """
            INSERT INTO privacy_consent_events (user_id, consent_type, policy_version, agreed)
            VALUES (:userId, :type, :policyVersion, :agreed)
            """.trimIndent(),
            mapOf(
                "userId" to userId,
                "type" to type.name,
                "policyVersion" to policyVersion,
                "agreed" to agreed,
            ),
        )
    }
}
