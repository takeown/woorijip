package com.woorijip.api.statistics

import com.woorijip.api.auth.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CreateSpendingAnalysisRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val question: String?,
)

@RestController
class SpendingAnalysisController(
    private val service: SpendingAnalysisService,
) {
    @PostMapping("/statistics/spending-answers")
    fun answer(
        currentUser: CurrentUser,
        @Valid @RequestBody request: CreateSpendingAnalysisRequest,
    ): SpendingAnalysisAnswer = service.answer(currentUser, requireNotNull(request.question).trim())
}
