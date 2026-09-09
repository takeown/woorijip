package com.woorijip.api.capture

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.TransactionCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.OffsetDateTime
import java.util.UUID

data class CaptureCandidate(
    @field:NotBlank @field:Size(max = 200) val merchant: String,
    @field:Positive val amount: Long,
    val occurredAt: OffsetDateTime,
    val category: TransactionCategory,
    val allowDuplicate: Boolean = false,
)
data class CaptureBatchRequest(
    val requestId: UUID,
    @field:Positive val payerId: Long,
    val cardIssuer: CardIssuer,
    @field:Valid @field:Size(min = 1, max = 50) val candidates: List<CaptureCandidate>,
)
data class CaptureCheck(val duplicate: Boolean, val category: TransactionCategory?)
data class CaptureApplyResponse(val savedCount: Int)

@RestController
class CaptureController(
    private val service: CaptureService,
    private val batchService: CaptureBatchService,
) {
    @PostMapping("/transaction-captures/analyze", consumes = ["multipart/form-data"])
    fun analyze(
        currentUser: CurrentUser,
        @RequestPart("file") file: MultipartFile,
        @RequestParam cardIssuer: CardIssuer,
        @RequestParam year: Int,
    ): GeneratedCapture {
        if (year !in 2000..2100) invalidCapture("기준 연도를 확인해 주세요.")
        if (cardIssuer !in setOf(CardIssuer.SHINHAN, CardIssuer.KB_KOOKMIN, CardIssuer.HYUNDAI)) invalidCapture("신한·국민·현대카드 캡처를 지원합니다.")
        if (file.size > 4 * 1024 * 1024) invalidCapture("캡처는 장당 4MB 이하여야 합니다.")
        return service.analyze(currentUser, file.bytes, cardIssuer, year)
    }

    @PostMapping("/transaction-captures/check")
    fun check(currentUser: CurrentUser, @Valid @RequestBody request: CaptureBatchRequest): List<CaptureCheck> =
        batchService.check(currentUser, request)

    @PostMapping("/transaction-captures/apply")
    fun apply(currentUser: CurrentUser, @Valid @RequestBody request: CaptureBatchRequest): CaptureApplyResponse =
        batchService.apply(currentUser, request)
}
