package com.woorijip.api.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class HealthResponse(
    val status: String,
)

@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(status = "ok")
}
