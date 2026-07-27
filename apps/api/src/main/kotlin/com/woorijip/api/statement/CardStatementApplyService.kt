package com.woorijip.api.statement

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.transaction.PaymentMethod
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

data class AppliedStatementTransaction(
    val sourceRow: Int,
    val transactionId: Long,
    val created: Boolean,
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
    ): List<AppliedStatementTransaction> {
        if (selections.isEmpty()) {
            throw InvalidCardStatementException("반영할 명세서 거래를 선택해 주세요.")
        }
        if (selections.map(StatementCandidateSelection::sourceRow).distinct().size != selections.size) {
            throw InvalidCardStatementException("같은 명세서 거래를 중복 선택할 수 없습니다.")
        }

        val statementImport = importRepository.findByIdForUpdate(importId, currentUser)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "명세서 가져오기를 찾을 수 없습니다.")
        val storedCandidates = importRepository.findCandidates(importId)
        val selectedCandidates = selections.map { selection ->
            val storedCandidate = storedCandidates.firstOrNull {
                it.candidate.sourceRow == selection.sourceRow
            } ?: throw InvalidCardStatementException("선택한 명세서 거래를 찾을 수 없습니다.")
            selection to storedCandidate
        }

        val unappliedCandidates = selectedCandidates
            .map(Pair<StatementCandidateSelection, StoredStatementCandidate>::second)
            .filter { it.appliedTransactionId == null }
        val matchesBySourceRow = if (unappliedCandidates.isEmpty()) {
            emptyMap()
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
            matcher
                .match(storedCandidates.map(StoredStatementCandidate::candidate), transactions)
                .associateBy { it.candidate.sourceRow }
        }

        return selectedCandidates.map { (selection, storedCandidate) ->
            val existingTransactionId = storedCandidate.appliedTransactionId
            if (existingTransactionId != null) {
                return@map AppliedStatementTransaction(
                    sourceRow = selection.sourceRow,
                    transactionId = existingTransactionId,
                    created = false,
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
            )
        }
    }

    private companion object {
        val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
