package com.woorijip.api.statistics

import com.woorijip.api.auth.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class AskSpendingQuestionRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val question: String?,
)

@RestController
class SpendingQuestionController(
    private val spendingQuestionService: SpendingQuestionService,
) {
    @PostMapping("/statistics/spending/questions")
    fun ask(
        currentUser: CurrentUser,
        @Valid @RequestBody request: AskSpendingQuestionRequest,
    ): SpendingQuestionAnswer =
        spendingQuestionService.answer(currentUser, requireNotNull(request.question).trim())
}
