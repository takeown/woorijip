package com.woorijip.api.statement

import com.woorijip.api.transaction.Transaction
import org.springframework.stereotype.Component
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale

enum class StatementMatchStatus {
    MATCHED,
    MISSING,
    DUPLICATE_SUSPECTED,
    MISMATCH,
}

data class StatementMatch(
    val candidate: StatementCandidate,
    val status: StatementMatchStatus,
    val transactionIds: List<Long>,
)

@Component
class CardStatementMatcher {
    fun match(
        candidates: List<StatementCandidate>,
        transactions: List<Transaction>,
    ): List<StatementMatch> {
        val results = mutableMapOf<Int, StatementMatch>()
        val usedTransactionIds = mutableSetOf<Long>()
        val transactionsBySignature = transactions.groupBy { it.signature() }

        candidates
            .groupBy { it.signature() }
            .forEach { (signature, candidateGroup) ->
                val transactionGroup = transactionsBySignature[signature].orEmpty()
                if (transactionGroup.size > candidateGroup.size) {
                    val transactionIds = transactionGroup.map { it.requiredId() }
                    candidateGroup.forEach { candidate ->
                        results[candidate.sourceRow] = StatementMatch(
                            candidate = candidate,
                            status = StatementMatchStatus.DUPLICATE_SUSPECTED,
                            transactionIds = transactionIds,
                        )
                    }
                    usedTransactionIds += transactionIds
                    return@forEach
                }

                candidateGroup
                    .zip(transactionGroup)
                    .forEach { (candidate, transaction) ->
                        val transactionId = transaction.requiredId()
                        results[candidate.sourceRow] = StatementMatch(
                            candidate = candidate,
                            status = StatementMatchStatus.MATCHED,
                            transactionIds = listOf(transactionId),
                        )
                        usedTransactionIds += transactionId
                    }
            }

        candidates
            .filterNot { results.containsKey(it.sourceRow) }
            .forEach { candidate ->
                val availableTransactions = transactions.filterNot {
                    usedTransactionIds.contains(it.requiredId())
                }
                val sameDateAndAmount = availableTransactions.filter {
                    it.occurredOn() == candidate.occurredOn &&
                        it.amount == candidate.approvedAmount
                }
                val equivalentMerchantMatches = sameDateAndAmount.filter {
                    merchantsAreEquivalent(candidate.merchant, it.merchant)
                }

                val result = when {
                    equivalentMerchantMatches.size == 1 ->
                        StatementMatch(
                            candidate = candidate,
                            status = StatementMatchStatus.MATCHED,
                            transactionIds = equivalentMerchantMatches.map { it.requiredId() },
                        )
                    equivalentMerchantMatches.size > 1 ->
                        StatementMatch(
                            candidate = candidate,
                            status = StatementMatchStatus.DUPLICATE_SUSPECTED,
                            transactionIds = equivalentMerchantMatches.map { it.requiredId() },
                        )
                    else -> matchNonExactCandidate(
                        candidate = candidate,
                        transactions = availableTransactions,
                    )
                }
                results[candidate.sourceRow] = result
                usedTransactionIds += result.transactionIds
            }

        return candidates.map { candidate ->
            checkNotNull(results[candidate.sourceRow])
        }
    }

    private fun matchNonExactCandidate(
        candidate: StatementCandidate,
        transactions: List<Transaction>,
    ): StatementMatch {
        val plausibleTransactions = transactions.filter { transaction ->
            val sameDate = transaction.occurredOn() == candidate.occurredOn
            sameDate && (
                transaction.amount == candidate.approvedAmount ||
                    merchantsAreEquivalent(candidate.merchant, transaction.merchant)
                )
        }
        val transactionIds = plausibleTransactions.map { it.requiredId() }
        val status = when (plausibleTransactions.size) {
            0 -> StatementMatchStatus.MISSING
            1 -> StatementMatchStatus.MISMATCH
            else -> StatementMatchStatus.DUPLICATE_SUSPECTED
        }
        return StatementMatch(
            candidate = candidate,
            status = status,
            transactionIds = transactionIds,
        )
    }

    private fun StatementCandidate.signature() = MatchSignature(
        occurredOn = occurredOn,
        amount = approvedAmount,
        merchant = normalizeMerchant(merchant),
    )

    private fun Transaction.signature() = MatchSignature(
        occurredOn = occurredOn(),
        amount = amount,
        merchant = normalizeMerchant(merchant),
    )

    private fun Transaction.occurredOn() =
        occurredAt.atZoneSameInstant(SEOUL_ZONE_ID).toLocalDate()

    private fun Transaction.requiredId(): Long = requireNotNull(id)

    private fun merchantsAreEquivalent(
        first: String,
        second: String,
    ): Boolean {
        val normalizedFirst = normalizeMerchant(first)
        val normalizedSecond = normalizeMerchant(second)
        if (normalizedFirst.isEmpty() || normalizedSecond.isEmpty()) {
            return false
        }
        return normalizedFirst == normalizedSecond ||
            normalizedFirst.contains(normalizedSecond) ||
            normalizedSecond.contains(normalizedFirst)
    }

    private fun normalizeMerchant(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, "")

    private data class MatchSignature(
        val occurredOn: java.time.LocalDate,
        val amount: Long,
        val merchant: String,
    )

    private companion object {
        val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
        val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]""")
    }
}
