package com.woorijip.api.health

import kotlin.test.Test
import kotlin.test.assertEquals

class HealthControllerTests {
    @Test
    fun `health returns ok`() {
        assertEquals(HealthResponse(status = "ok"), HealthController().health())
    }
}
