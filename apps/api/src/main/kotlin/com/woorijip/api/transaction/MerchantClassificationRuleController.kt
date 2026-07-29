package com.woorijip.api.transaction

import com.woorijip.api.auth.CurrentUser
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class MerchantClassificationRecommendationResponse(
    val ruleId: Long,
    val category: TransactionCategory,
    val tags: Set<TransactionTag>,
    val source: ClassificationSource = ClassificationSource.MERCHANT_RULE,
)

@Validated
@RestController
class MerchantClassificationRuleController(
    private val service: MerchantClassificationRuleService,
) {
    @GetMapping("/merchant-classification-rules/recommendation")
    fun findRecommendation(
        currentUser: CurrentUser,
        @RequestParam @Size(max = 200) merchant: String,
    ): ResponseEntity<MerchantClassificationRecommendationResponse> {
        val recommendation = service.findRecommendation(currentUser, merchant)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(
            MerchantClassificationRecommendationResponse(
                ruleId = recommendation.id,
                category = recommendation.category,
                tags = recommendation.tags,
            ),
        )
    }
}
