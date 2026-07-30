package com.woorijip.api.ai

import com.woorijip.api.error.ApiException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiSensitiveInputGuardTests {
    private val guard = AiSensitiveInputGuard()

    @Test
    fun `detects sensitive identifiers and credentials`() {
        assertTrue(guard.containsSensitiveData("카드번호 4111-1111-1111-1111"))
        assertTrue(guard.containsSensitiveData("계좌번호 123-456-789012"))
        assertTrue(guard.containsSensitiveData("주민번호 900101-1234567"))
        assertTrue(guard.containsSensitiveData("메일은 user@example.com"))
        assertTrue(guard.containsSensitiveData("전화번호 010-1234-5678"))
        assertTrue(guard.containsSensitiveData("Bearer abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `allows ordinary transaction details`() {
        assertFalse(guard.containsSensitiveData("오늘 김밥천국에서 신한카드로 8천원 썼어"))
        assertFalse(guard.containsSensitiveData("이마트에서 12345678원 결제"))
    }

    @Test
    fun `rejects the whole request when any message is sensitive`() {
        assertFailsWith<ApiException> {
            guard.requireSafe(listOf("김밥천국 8천원", "계좌번호 123-456-789012"))
        }
    }
}
