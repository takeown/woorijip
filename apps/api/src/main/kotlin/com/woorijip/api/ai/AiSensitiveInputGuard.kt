package com.woorijip.api.ai

import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import org.springframework.stereotype.Component

@Component
class AiSensitiveInputGuard {
    fun requireSafe(messages: List<String>) {
        if (messages.any(::containsSensitiveData)) {
            throw ApiException(
                ErrorCode.SENSITIVE_AI_INPUT,
                "카드번호, 계좌번호, 주민등록번호, 이메일, 전화번호 또는 인증정보를 제거한 뒤 다시 입력해 주세요.",
            )
        }
    }

    internal fun containsSensitiveData(message: String): Boolean =
        EMAIL.containsMatchIn(message) ||
            PHONE_NUMBER.containsMatchIn(message) ||
            RESIDENT_REGISTRATION_NUMBER.containsMatchIn(message) ||
            SECRET_TOKEN.containsMatchIn(message) ||
            JWT.containsMatchIn(message) ||
            CREDENTIAL_AFTER_KEYWORD.containsMatchIn(message) ||
            accountNumberAfterKeyword(message) ||
            CARD_NUMBER_CANDIDATE.findAll(message).any { match -> isCardNumber(match.value) }

    private fun accountNumberAfterKeyword(message: String): Boolean =
        ACCOUNT_NUMBER_AFTER_KEYWORD
            .findAll(message)
            .map { match -> match.groupValues[1].filter(Char::isDigit) }
            .any { digits -> digits.length in 8..16 }

    private fun isCardNumber(candidate: String): Boolean {
        val digits = candidate.filter(Char::isDigit)
        if (digits.length !in 13..19) return false

        val sum = digits.reversed().mapIndexed { index, character ->
            val digit = character.digitToInt()
            if (index % 2 == 1) {
                (digit * 2).let { doubled -> if (doubled > 9) doubled - 9 else doubled }
            } else {
                digit
            }
        }.sum()
        return sum % 10 == 0
    }

    private companion object {
        val EMAIL = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")
        val PHONE_NUMBER = Regex("""(?<!\d)01[016789][-\s]?\d{3,4}[-\s]?\d{4}(?!\d)""")
        val RESIDENT_REGISTRATION_NUMBER = Regex("""(?<!\d)\d{6}[-\s]?[1-4]\d{6}(?!\d)""")
        val SECRET_TOKEN = Regex(
            """(?i)\b(?:Bearer\s+\S{16,}|sk-[A-Z0-9_-]{16,}|ya29\.[A-Z0-9_-]{8,}|ghp_[A-Z0-9]{16,}|github_pat_[A-Z0-9_]{16,})""",
        )
        val JWT = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""")
        val CREDENTIAL_AFTER_KEYWORD = Regex(
            """(?i)(?:비밀번호|패스워드|인증(?:번호|코드|정보)|password|passwd|access[_\s-]?token|refresh[_\s-]?token|client[_\s-]?secret)\s*[:：=]?\s*\S{4,}""",
        )
        val ACCOUNT_NUMBER_AFTER_KEYWORD =
            Regex("""(?:계좌(?:번호)?|입금\s*계좌)\s*[:：]?\s*([0-9][0-9\s-]{6,24}[0-9])""")
        val CARD_NUMBER_CANDIDATE = Regex("""(?<!\d)(?:\d[\s-]?){13,19}(?!\d)""")
    }
}
