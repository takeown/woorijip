package com.woorijip.api.ai

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class OpenAiSafetyIdentifier(
    private val properties: OpenAiProperties,
) {
    fun forUser(userId: Long): String {
        val secret = properties.safetyIdentifierSecret
        if (secret.isBlank()) {
            throw DraftGenerationException("OpenAI safety_identifier 비밀값이 설정되지 않았습니다.")
        }

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM))
        val digest = mac.doFinal("user:$userId".toByteArray(StandardCharsets.UTF_8))
        return "$PREFIX${Base64.getUrlEncoder().withoutPadding().encodeToString(digest)}"
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val PREFIX = "usr_"
    }
}
