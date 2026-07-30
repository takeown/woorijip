package com.woorijip.api.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpenAiSafetyIdentifierTests {
    @Test
    fun `creates a stable pseudonymous identifier per user`() {
        val identifier = OpenAiSafetyIdentifier(
            OpenAiProperties(safetyIdentifierSecret = "test-secret-with-enough-entropy"),
        )

        val first = identifier.forUser(123)
        val second = identifier.forUser(123)

        assertEquals(first, second)
        assertTrue(first.startsWith("usr_"))
        assertTrue("123" !in first)
        assertNotEquals(first, identifier.forUser(456))
    }

    @Test
    fun `requires a safety identifier secret`() {
        val identifier = OpenAiSafetyIdentifier(OpenAiProperties())

        assertFailsWith<DraftGenerationException> {
            identifier.forUser(123)
        }
    }
}
