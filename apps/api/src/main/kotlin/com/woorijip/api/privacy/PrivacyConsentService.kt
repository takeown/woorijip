package com.woorijip.api.privacy

import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class PrivacyConsentStatus(
    val privacyPolicyVersion: String,
    val privacyPolicyAgreed: Boolean,
    val privacyPolicyAgreedAt: String?,
    val aiOverseasTransferVersion: String,
    val aiOverseasTransferAgreed: Boolean,
    val aiOverseasTransferAgreedAt: String?,
)

@Service
class PrivacyConsentService(
    private val repository: PrivacyConsentRepository,
) {
    @Transactional(readOnly = true)
    fun status(userId: Long): PrivacyConsentStatus {
        val privacy = repository.latest(userId, PrivacyConsentType.PRIVACY_POLICY)
        val ai = repository.latest(userId, PrivacyConsentType.AI_OVERSEAS_TRANSFER)
        val privacyAgreed = privacy?.policyVersion == PRIVACY_POLICY_VERSION && privacy.agreed
        val aiAgreed = ai?.policyVersion == AI_OVERSEAS_TRANSFER_VERSION && ai.agreed
        return PrivacyConsentStatus(
            privacyPolicyVersion = PRIVACY_POLICY_VERSION,
            privacyPolicyAgreed = privacyAgreed,
            privacyPolicyAgreedAt = privacy?.createdAt?.toString()?.takeIf { privacyAgreed },
            aiOverseasTransferVersion = AI_OVERSEAS_TRANSFER_VERSION,
            aiOverseasTransferAgreed = aiAgreed,
            aiOverseasTransferAgreedAt = ai?.createdAt?.toString()?.takeIf { aiAgreed },
        )
    }

    @Transactional
    fun update(userId: Long, privacyPolicyAgreed: Boolean, aiOverseasTransferAgreed: Boolean): PrivacyConsentStatus {
        if (!privacyPolicyAgreed) {
            throw ApiException(
                ErrorCode.PRIVACY_CONSENT_REQUIRED,
                "개인정보 처리방침 동의가 필요합니다.",
            )
        }
        repository.record(userId, PrivacyConsentType.PRIVACY_POLICY, PRIVACY_POLICY_VERSION, true)
        repository.record(userId, PrivacyConsentType.AI_OVERSEAS_TRANSFER, AI_OVERSEAS_TRANSFER_VERSION, aiOverseasTransferAgreed)
        return status(userId)
    }

    @Transactional(readOnly = true)
    fun requireAiOverseasTransfer(userId: Long) {
        if (!hasAiOverseasTransfer(userId)) {
            throw ApiException(
                ErrorCode.AI_OVERSEAS_TRANSFER_CONSENT_REQUIRED,
                "OpenAI 국외 이전 동의 후 사용할 수 있습니다.",
            )
        }
    }

    @Transactional(readOnly = true)
    fun hasAiOverseasTransfer(userId: Long): Boolean {
        val latest = repository.latest(userId, PrivacyConsentType.AI_OVERSEAS_TRANSFER)
        return latest?.policyVersion == AI_OVERSEAS_TRANSFER_VERSION && latest.agreed
    }

    companion object {
        const val PRIVACY_POLICY_VERSION = "2026-09-09"
        const val AI_OVERSEAS_TRANSFER_VERSION = "2026-09-09"
    }
}
