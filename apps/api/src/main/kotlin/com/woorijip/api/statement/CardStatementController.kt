package com.woorijip.api.statement

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.storedvalue.StoredValueAutomationKey
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.Transaction
import com.woorijip.api.transaction.TransactionCategory
import com.woorijip.api.transaction.TransactionTag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
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
    val storedValueAccountType: StoredValueAutomationKey?,
    val matchStatus: StatementMatchStatus,
    val transactionIds: List<Long>,
    val relatedTransactions: List<StatementTransactionResponse>,
)

data class StatementTransactionResponse(
    val id: Long,
    val merchant: String,
    val description: String?,
    val amount: Long,
    val category: TransactionCategory,
    val tags: Set<TransactionTag>,
    val occurredAt: OffsetDateTime,
)

data class ApplyCardStatementRequest(
    val candidates: List<@Valid ApplyStatementCandidateRequest> = emptyList(),
    val corrections: List<@Valid CorrectStatementCandidateRequest> = emptyList(),
)

data class ApplyStatementCandidateRequest(
    @field:Positive
    val sourceRow: Int,
    @field:NotNull
    val category: TransactionCategory?,
    @field:Size(max = 3)
    val tags: Set<TransactionTag> = emptySet(),
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
    private val previewService: CardStatementPreviewService,
    private val applyService: CardStatementApplyService,
) {
    @PostMapping(
        "/preview",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    fun preview(
        currentUser: CurrentUser,
        @RequestPart("file") file: MultipartFile,
    ): CardStatementPreviewResponse {
        return previewService.preview(currentUser, file).toResponse()
    }

    @PostMapping("/{importId}/apply")
    fun apply(
        currentUser: CurrentUser,
        @PathVariable importId: Long,
        @Valid @RequestBody request: ApplyCardStatementRequest,
    ): ApplyCardStatementResponse {
        return ApplyCardStatementResponse(
            transactions = applyService.apply(
                currentUser = currentUser,
                importId = importId,
                selections = request.candidates.map { candidate ->
                    StatementCandidateSelection(
                        sourceRow = candidate.sourceRow,
                        category = requireNotNull(candidate.category),
                        tags = candidate.tags,
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
        storedValueAccountType = candidate.storedValueAccountType,
        matchStatus = status,
        transactionIds = transactionIds,
        relatedTransactions = transactionIds.mapNotNull(transactionsById::get).map { transaction ->
            StatementTransactionResponse(
                id = requireNotNull(transaction.id),
                merchant = transaction.merchant,
                description = transaction.description,
                amount = transaction.amount,
                category = transaction.category,
                tags = transaction.tags,
                occurredAt = transaction.occurredAt,
            )
        },
    )
