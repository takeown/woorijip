package com.woorijip.api.statement

import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.Transaction
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.OffsetDateTime

data class CardStatementPreviewResponse(
    val importId: Long,
    val cardIssuer: CardIssuer,
    val statementMonth: String,
    val totalCount: Int,
    val totalBilledAmount: Long,
    val adjustmentCount: Int,
    val matchedCount: Int,
    val missingCount: Int,
    val duplicateSuspectedCount: Int,
    val mismatchCount: Int,
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
    val matchStatus: StatementMatchStatus,
    val transactionIds: List<Long>,
    val relatedTransactions: List<StatementTransactionResponse>,
)

data class StatementTransactionResponse(
    val id: Long,
    val merchant: String,
    val description: String?,
    val amount: Long,
    val category: String,
    val occurredAt: OffsetDateTime,
)

data class ApplyCardStatementRequest(
    val candidates: List<@Valid ApplyStatementCandidateRequest> = emptyList(),
    val corrections: List<@Valid CorrectStatementCandidateRequest> = emptyList(),
)

data class ApplyStatementCandidateRequest(
    @field:Positive
    val sourceRow: Int,
    @field:NotBlank
    @field:Size(max = 100)
    val category: String,
    @field:Size(max = 500)
    val description: String?,
)

data class CorrectStatementCandidateRequest(
    @field:Positive
    val sourceRow: Int,
    @field:Positive
    val transactionId: Long,
    @field:NotBlank
    @field:Size(max = 200)
    val expectedMerchant: String,
    @field:Positive
    val expectedAmount: Long,
)

data class ApplyCardStatementResponse(
    val transactions: List<AppliedStatementTransaction>,
)

@RestController
@RequestMapping("/card-statements")
class CardStatementController(
    private val googleAccountService: GoogleAccountService,
    private val previewService: CardStatementPreviewService,
    private val applyService: CardStatementApplyService,
) {
    @PostMapping(
        "/preview",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun preview(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @RequestPart("file") file: MultipartFile,
    ): CardStatementPreviewResponse =
        try {
            val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
            previewService.preview(currentUser, file).toResponse()
        } catch (exception: InvalidCardStatementException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                exception.message,
                exception,
            )
        }

    @PostMapping("/{importId}/apply")
    fun apply(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @PathVariable importId: Long,
        @Valid @RequestBody request: ApplyCardStatementRequest,
    ): ApplyCardStatementResponse =
        try {
            val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
            ApplyCardStatementResponse(
                transactions = applyService.apply(
                    currentUser = currentUser,
                    importId = importId,
                    selections = request.candidates.map { candidate ->
                        StatementCandidateSelection(
                            sourceRow = candidate.sourceRow,
                            category = candidate.category.trim(),
                            description = candidate.description?.trim()?.takeIf(String::isNotEmpty),
                        )
                    },
                    corrections = request.corrections.map { correction ->
                        StatementCandidateCorrection(
                            sourceRow = correction.sourceRow,
                            transactionId = correction.transactionId,
                            expectedMerchant = correction.expectedMerchant,
                            expectedAmount = correction.expectedAmount,
                        )
                    },
                ),
            )
        } catch (exception: InvalidCardStatementException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                exception.message,
                exception,
            )
        }
}

private fun CardStatementPreview.toResponse(): CardStatementPreviewResponse =
    CardStatementPreviewResponse(
        importId = importId,
        cardIssuer = statement.cardIssuer,
        statementMonth = statement.statementMonth.toString(),
        totalCount = statement.totalCount,
        totalBilledAmount = statement.totalBilledAmount,
        adjustmentCount = statement.adjustments.size,
        matchedCount = matches.count { it.status == StatementMatchStatus.MATCHED },
        missingCount = matches.count { it.status == StatementMatchStatus.MISSING },
        duplicateSuspectedCount = matches.count {
            it.status == StatementMatchStatus.DUPLICATE_SUSPECTED
        },
        mismatchCount = matches.count { it.status == StatementMatchStatus.MISMATCH },
        candidates = matches.map { match -> match.toResponse(transactionsById) },
    )

private fun StatementMatch.toResponse(
    transactionsById: Map<Long, Transaction>,
): StatementCandidateResponse =
    StatementCandidateResponse(
        sourceRow = candidate.sourceRow,
        occurredOn = candidate.occurredOn,
        cardLabel = candidate.cardLabel,
        merchant = candidate.merchant,
        approvedAmount = candidate.approvedAmount,
        billedAmount = candidate.billedAmount,
        interestAmount = candidate.interestAmount,
        type = candidate.type,
        installmentMonths = candidate.installmentMonths,
        installmentSequence = candidate.installmentSequence,
        remainingInstallments = candidate.remainingInstallments,
        remainingPrincipal = candidate.remainingPrincipal,
        matchStatus = status,
        transactionIds = transactionIds,
        relatedTransactions = transactionIds.mapNotNull(transactionsById::get).map { transaction ->
            StatementTransactionResponse(
                id = requireNotNull(transaction.id),
                merchant = transaction.merchant,
                description = transaction.description,
                amount = transaction.amount,
                category = transaction.category,
                occurredAt = transaction.occurredAt,
            )
        },
    )
