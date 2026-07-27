package com.woorijip.api.statement

import com.woorijip.api.transaction.CardIssuer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

data class CardStatementPreviewResponse(
    val cardIssuer: CardIssuer,
    val statementMonth: String,
    val totalCount: Int,
    val totalBilledAmount: Long,
    val adjustmentCount: Int,
    val candidates: List<StatementCandidateResponse>,
)

data class StatementCandidateResponse(
    val sourceRow: Int,
    val occurredOn: LocalDate,
    val cardLabel: String,
    val merchant: String,
    val approvedAmount: Long,
    val billedAmount: Long,
    val interestAmount: Long,
    val type: StatementEntryType,
    val installmentMonths: Int?,
    val installmentSequence: Int?,
    val remainingInstallments: Int?,
    val remainingPrincipal: Long?,
)

@RestController
@RequestMapping("/card-statements")
class CardStatementController(
    private val previewService: CardStatementPreviewService,
) {
    @PostMapping(
        "/preview",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun preview(
        @RequestPart("file") file: MultipartFile,
    ): CardStatementPreviewResponse =
        try {
            previewService.preview(file).toResponse()
        } catch (exception: InvalidCardStatementException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                exception.message,
                exception,
            )
        }
}

private fun ParsedCardStatement.toResponse(): CardStatementPreviewResponse =
    CardStatementPreviewResponse(
        cardIssuer = cardIssuer,
        statementMonth = statementMonth.toString(),
        totalCount = totalCount,
        totalBilledAmount = totalBilledAmount,
        adjustmentCount = adjustments.size,
        candidates = candidates.map(StatementCandidate::toResponse),
    )

private fun StatementCandidate.toResponse(): StatementCandidateResponse =
    StatementCandidateResponse(
        sourceRow = sourceRow,
        occurredOn = occurredOn,
        cardLabel = cardLabel,
        merchant = merchant,
        approvedAmount = approvedAmount,
        billedAmount = billedAmount,
        interestAmount = interestAmount,
        type = type,
        installmentMonths = installmentMonths,
        installmentSequence = installmentSequence,
        remainingInstallments = remainingInstallments,
        remainingPrincipal = remainingPrincipal,
    )
