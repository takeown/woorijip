package com.woorijip.api.statistics

import com.woorijip.api.ai.AiSensitiveInputGuard
import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.transaction.TransactionCategory
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class SpendingAnalysisPayer {
    ALL,
    ME,
    PARTNER,
}

data class SpendingAnalysisQuery(
    val from: String,
    val to: String,
    val category: String,
    val payer: SpendingAnalysisPayer,
)

data class SpendingAnalysisSummary(
    val category: String,
    val totalAmount: Long,
    val transactionCount: Long,
)

data class SpendingAnalysisQueryResult(
    val from: String,
    val to: String,
    val totalAmount: Long,
    val transactionCount: Long,
    val categories: List<SpendingAnalysisSummary>,
    val evidence: List<SpendingAnalysisRecord>,
    val evidenceLimited: Boolean,
)

class SpendingAnalysisTools(
    private val repository: SpendingAnalysisRepository,
    private val currentUser: CurrentUser,
    private val sensitiveInputGuard: AiSensitiveInputGuard,
    private val transactionLimit: Int,
) {
    private val evidenceTransactions: MutableMap<String, SpendingAnalysisTransaction> = linkedMapOf()
    val transactionsByReference: Map<String, SpendingAnalysisTransaction>
        get() = evidenceTransactions
    var dataLimited: Boolean = false
        private set
    var queryCount: Int = 0
        private set
    var matchedTransactions: Long = 0
        private set
    var successfulQueries: Int = 0
        private set

    fun query(query: SpendingAnalysisQuery): SpendingAnalysisQueryResult {
        if (++queryCount > MAX_QUERIES) {
            throw SpendingAnalysisGenerationException("가계 분석 조회 횟수를 초과했습니다.")
        }
        val from = try {
            LocalDate.parse(query.from)
        } catch (_: RuntimeException) {
            throw SpendingAnalysisGenerationException("가계 분석 시작일 형식이 잘못되었습니다.")
        }
        val to = try {
            LocalDate.parse(query.to)
        } catch (_: RuntimeException) {
            throw SpendingAnalysisGenerationException("가계 분석 종료일 형식이 잘못되었습니다.")
        }
        if (from.year !in 2000..2100 || to.year !in 2000..2100 || ChronoUnit.DAYS.between(from, to) !in 0..365) {
            throw SpendingAnalysisGenerationException("가계 분석은 한 번에 최대 366일만 조회할 수 있습니다.")
        }
        val category = if (query.category == "ALL") null else {
            TransactionCategory.entries.firstOrNull { it.name == query.category }
                ?: throw SpendingAnalysisGenerationException("지원하지 않는 가계 분석 카테고리입니다.")
        }
        val start = from.atStartOfDay(SEOUL).toOffsetDateTime()
        val endExclusive = to.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime()
        val categories = repository.summarize(currentUser.householdId, currentUser.id, start, endExclusive, category, query.payer)
        val count = categories.sumOf(SpendingAnalysisSummary::transactionCount)
        val remaining = (transactionLimit.coerceIn(1, 200) - evidenceTransactions.size).coerceIn(0, 5)
        val loaded = repository.transactions(
            currentUser.householdId, currentUser.id, start, endExclusive, category, query.payer, remaining + 1,
        )
        val safe = loaded.take(remaining).filter { sensitiveInputGuard.isSafeForExternalProcessing(it.merchant) }
        successfulQueries++
        matchedTransactions += count
        val limited = loaded.size > remaining || safe.size < loaded.take(remaining).size
        dataLimited = dataLimited || limited
        val records = safe.map { transaction ->
            val reference = evidenceTransactions.entries.firstOrNull { it.value.id == transaction.id }?.key
                ?: "T${evidenceTransactions.size + 1}".also { evidenceTransactions[it] = transaction }
            SpendingAnalysisRecord(
                reference = reference,
                merchant = transaction.merchant,
                amount = transaction.amount,
                occurredDate = transaction.occurredAt.atZoneSameInstant(SEOUL).toLocalDate(),
                payer = if (transaction.payerId == currentUser.id) "ME" else "PARTNER",
                paymentMethod = transaction.paymentMethod,
                category = transaction.category,
                tags = transaction.tags,
            )
        }
        return SpendingAnalysisQueryResult(
            from = query.from,
            to = query.to,
            totalAmount = categories.sumOf(SpendingAnalysisSummary::totalAmount),
            transactionCount = count,
            categories = categories,
            evidence = records,
            evidenceLimited = limited,
        )
    }

    companion object {
        const val MAX_QUERIES: Int = 4
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
