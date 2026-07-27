package com.woorijip.api.statement

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.Transaction
import com.woorijip.api.transaction.TransactionDraft
import com.woorijip.api.transaction.TransactionRepository
import com.woorijip.api.transaction.TransactionService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneId

data class StatementCandidateSelection(
    val sourceRow: Int,
    val category: String,
    val description: String?,
)

data class StatementCandidateCorrection(
    val sourceRow: Int,
    val transactionId: Long,
    val expectedMerchant: String,
    val expectedAmount: Long,
)

data class AppliedStatementTransaction(
    val sourceRow: Int,
    val transactionId: Long,
    val created: Boolean,
    val updated: Boolean,
)

@Service
class CardStatementApplyService(
    private val importRepository: CardStatementImportRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionService: TransactionService,
    private val matcher: CardStatementMatcher,
) {
    @Transactional
    fun apply(
        currentUser: CurrentUser,
        importId: Long,
        selections: List<StatementCandidateSelection>,
        corrections: List<StatementCandidateCorrection>,
    ): List<AppliedStatementTransaction> {
        if (selections.isEmpty() && corrections.isEmpty()) {
            throw InvalidCardStatementException("반영할 명세서 거래를 선택해 주세요.")
        }
        val selectedSourceRows =
            selections.map(StatementCandidateSelection::sourceRow) +
                corrections.map(StatementCandidateCorrection::sourceRow)
        if (selectedSourceRows.distinct().size != selectedSourceRows.size) {
            throw InvalidCardStatementException("같은 명세서 거래를 중복 선택할 수 없습니다.")
        }
        if (
            corrections.map(StatementCandidateCorrection::transactionId).distinct().size !=
            corrections.size
        ) {
            throw InvalidCardStatementException("같은 기존 거래를 중복 수정할 수 없습니다.")
        }

        val statementImport = importRepository.findByIdForUpdate(importId, currentUser)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "명세서 가져오기를 찾을 수 없습니다.")
        val storedCandidates = importRepository.findCandidates(importId)
        val selectedCandidatesByRow = selectedSourceRows.associateWith { sourceRow ->
            val storedCandidate = storedCandidates.firstOrNull {
                it.candidate.sourceRow == sourceRow
            } ?: throw InvalidCardStatementException("선택한 명세서 거래를 찾을 수 없습니다.")
            storedCandidate
        }

        val unappliedCandidates = selectedCandidatesByRow.values
            .filter { it.appliedTransactionId == null }
        val matchesBySourceRow: Map<Int, StatementMatch>
        val transactionsById: Map<Long, Transaction>
        if (unappliedCandidates.isEmpty()) {
            matchesBySourceRow = emptyMap()
            transactionsById = emptyMap()
        } else {
            val firstDate = storedCandidates.minOf { it.candidate.occurredOn }
            val lastDate = storedCandidates.maxOf { it.candidate.occurredOn }
            val transactions = transactionRepository
                .findAllByHouseholdIdAndPayerIdAndCardIssuerAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                    householdId = currentUser.householdId,
                    payerId = currentUser.id,
                    cardIssuer = statementImport.cardIssuer,
                    occurredAtFrom = firstDate.atStartOfDay(SEOUL_ZONE_ID).toOffsetDateTime(),
                    occurredAtTo = lastDate.plusDays(1).atStartOfDay(SEOUL_ZONE_ID).toOffsetDateTime(),
                )
            matchesBySourceRow = matcher
                .match(storedCandidates.map(StoredStatementCandidate::candidate), transactions)
                .associateBy { it.candidate.sourceRow }
            transactionsById = transactions.associateBy { requireNotNull(it.id) }
        }

        val createdTransactions = selections.map { selection ->
            val storedCandidate = requireNotNull(selectedCandidatesByRow[selection.sourceRow])
            val existingTransactionId = storedCandidate.appliedTransactionId
            if (existingTransactionId != null) {
                return@map AppliedStatementTransaction(
                    sourceRow = selection.sourceRow,
                    transactionId = existingTransactionId,
                    created = false,
                    updated = false,
                )
            }

            val candidate = storedCandidate.candidate
            if (candidate.approvedAmount <= 0 || candidate.type == StatementEntryType.REVERSAL) {
                throw InvalidCardStatementException("취소 또는 0원 이하 거래는 새 거래로 반영할 수 없습니다.")
            }
            val match = requireNotNull(matchesBySourceRow[candidate.sourceRow])
            if (match.status != StatementMatchStatus.MISSING) {
                throw InvalidCardStatementException("현재 누락 상태인 명세서 거래만 반영할 수 있습니다.")
            }

            val transaction = transactionService.create(
                currentUser = currentUser,
                draft = TransactionDraft(
                    payerId = currentUser.id,
                    merchant = candidate.merchant,
                    description = selection.description,
                    amount = candidate.approvedAmount,
                    category = selection.category,
                    paymentMethod = PaymentMethod.CARD,
                    cardIssuer = statementImport.cardIssuer,
                    occurredAt = candidate.occurredOn
                        .atTime(12, 0)
                        .atZone(SEOUL_ZONE_ID)
                        .toOffsetDateTime(),
                ),
            )
            val transactionId = requireNotNull(transaction.id)
            importRepository.markApplied(importId, candidate.sourceRow, transactionId)
            AppliedStatementTransaction(
                sourceRow = candidate.sourceRow,
                transactionId = transactionId,
                created = true,
                updated = false,
            )
        }

        val updatedTransactions = corrections.map { correction ->
            val storedCandidate = requireNotNull(selectedCandidatesByRow[correction.sourceRow])
            val existingTransactionId = storedCandidate.appliedTransactionId
            if (existingTransactionId != null) {
                if (existingTransactionId != correction.transactionId) {
                    throw InvalidCardStatementException("선택한 기존 거래가 명세서 반영 기록과 일치하지 않습니다.")
                }
                return@map AppliedStatementTransaction(
                    sourceRow = correction.sourceRow,
                    transactionId = existingTransactionId,
                    created = false,
                    updated = false,
                )
            }

            val candidate = storedCandidate.candidate
            if (candidate.approvedAmount <= 0 || candidate.type == StatementEntryType.REVERSAL) {
                throw InvalidCardStatementException("취소 또는 0원 이하 거래로 기존 거래를 수정할 수 없습니다.")
            }
            val match = requireNotNull(matchesBySourceRow[candidate.sourceRow])
            if (
                match.status == StatementMatchStatus.MATCHED &&
                match.transactionIds == listOf(correction.transactionId)
            ) {
                return@map AppliedStatementTransaction(
                    sourceRow = candidate.sourceRow,
                    transactionId = correction.transactionId,
                    created = false,
                    updated = false,
                )
            }
            if (
                match.status != StatementMatchStatus.MISMATCH ||
                match.transactionIds != listOf(correction.transactionId)
            ) {
                throw InvalidCardStatementException("현재 단일 불일치 상태인 거래만 수정할 수 있습니다.")
            }
            val transaction = transactionsById[correction.transactionId]
                ?: throw InvalidCardStatementException("수정할 기존 거래를 찾을 수 없습니다.")
            if (
                transaction.merchant != correction.expectedMerchant ||
                transaction.amount != correction.expectedAmount
            ) {
                throw InvalidCardStatementException("기존 거래가 변경되었습니다. 명세서를 다시 대조해 주세요.")
            }
            val updated = transactionRepository.updateStatementDetailsIfUnchanged(
                id = correction.transactionId,
                householdId = currentUser.householdId,
                payerId = currentUser.id,
                cardIssuer = statementImport.cardIssuer.name,
                expectedMerchant = correction.expectedMerchant,
                expectedAmount = correction.expectedAmount,
                expectedOccurredAt = transaction.occurredAt,
                merchant = candidate.merchant,
                amount = candidate.approvedAmount,
            )
            if (updated != 1) {
                throw InvalidCardStatementException("대조 결과가 변경되었습니다. 명세서를 다시 대조해 주세요.")
            }
            AppliedStatementTransaction(
                sourceRow = candidate.sourceRow,
                transactionId = correction.transactionId,
                created = false,
                updated = true,
            )
        }

        return createdTransactions + updatedTransactions
    }

    private companion object {
        val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
