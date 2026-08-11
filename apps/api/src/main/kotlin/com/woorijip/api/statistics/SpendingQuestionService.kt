package com.woorijip.api.statistics

import com.woorijip.api.ai.AiSensitiveInputGuard
import com.woorijip.api.ai.GeneratedSpendingQuestion
import com.woorijip.api.ai.GeneratedSpendingQuestionStatus
import com.woorijip.api.ai.OpenAiProperties
import com.woorijip.api.ai.SpendingQuestionIntent
import com.woorijip.api.ai.SpendingQuestionInterpretationContext
import com.woorijip.api.ai.SpendingQuestionInterpretationException
import com.woorijip.api.ai.SpendingQuestionInterpreter
import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import com.woorijip.api.transaction.TransactionCategory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SpendingQuestionAnswerStatus {
    ANSWERED,
    UNSUPPORTED,
}

data class SpendingQuestionAnswer(
    val status: SpendingQuestionAnswerStatus,
    val message: String,
    val intent: SpendingQuestionIntent? = null,
    val period: SpendingPeriod? = null,
    val payer: SpendingPayer? = null,
    val referenceDate: LocalDate? = null,
    val startDate: LocalDate? = null,
    val endDateExclusive: LocalDate? = null,
    val category: TransactionCategory? = null,
    val categoryLabel: String? = null,
    val currentAmount: Long? = null,
    val previousAmount: Long? = null,
    val amountChange: Long? = null,
    val changeRatePercent: BigDecimal? = null,
    val evidenceTransactions: List<SpendingEvidenceTransaction> = emptyList(),
    val remainingRequestsToday: Int,
)

@Service
class SpendingQuestionService(
    private val interpreter: SpendingQuestionInterpreter,
    private val statisticsService: SpendingStatisticsService,
    private val sensitiveInputGuard: AiSensitiveInputGuard,
    private val usageRepository: SpendingQuestionUsageRepository,
    private val openAiProperties: OpenAiProperties,
) {
    fun answer(
        currentUser: CurrentUser,
        question: String,
    ): SpendingQuestionAnswer {
        sensitiveInputGuard.requireSafe(listOf(question))
        val currentDate = LocalDate.now(SEOUL)
        val dailyLimit = openAiProperties.spendingQuestionDailyLimit
        val requestCount = if (dailyLimit > 0) {
            usageRepository.consume(currentUser.id, currentDate, dailyLimit)
        } else {
            null
        } ?: throw ApiException(
            ErrorCode.AI_USAGE_LIMIT_EXCEEDED,
            "오늘 사용할 수 있는 가계 질문 ${dailyLimit.coerceAtLeast(0)}회를 모두 사용했습니다. 내일 다시 질문해 주세요.",
        )
        val remainingRequests = dailyLimit - requestCount

        val generated = try {
            interpreter.interpret(
                question,
                SpendingQuestionInterpretationContext(
                    currentUserId = currentUser.id,
                    currentDate = currentDate,
                ),
            )
        } catch (exception: SpendingQuestionInterpretationException) {
            logger.warn("AI 가계 질문 해석 실패: {}", exception.message)
            throw ApiException(
                ErrorCode.AI_ANALYSIS_UNAVAILABLE,
                "가계 질문을 해석하지 못했습니다. 잠시 후 다시 시도해 주세요.",
            )
        }

        if (generated.status == GeneratedSpendingQuestionStatus.UNSUPPORTED) {
            return SpendingQuestionAnswer(
                status = SpendingQuestionAnswerStatus.UNSUPPORTED,
                message = "현재는 기간별 총지출, 카테고리 지출과 가장 큰 지출만 답할 수 있습니다.",
                remainingRequestsToday = remainingRequests,
            )
        }

        val query = requireValidQuery(generated)
        val statistics = statisticsService.find(
            currentUser = currentUser,
            period = query.period,
            payer = query.payer,
            referenceDate = query.referenceDate,
        )
        val evidence = statisticsService.findEvidence(
            currentUser = currentUser,
            payer = query.payer,
            startDate = statistics.startDate,
            endDateExclusive = statistics.endDateExclusive,
            category = query.category,
            limit = EVIDENCE_LIMIT,
        )

        return when (query.intent) {
            SpendingQuestionIntent.TOTAL -> totalAnswer(query, statistics, evidence, remainingRequests)
            SpendingQuestionIntent.CATEGORY -> categoryAnswer(query, statistics, evidence, remainingRequests)
            SpendingQuestionIntent.LARGEST_TRANSACTION ->
                largestTransactionAnswer(query, statistics, evidence, remainingRequests)
        }
    }

    private fun totalAnswer(
        query: ValidSpendingQuestion,
        statistics: SpendingStatistics,
        evidence: List<SpendingEvidenceTransaction>,
        remainingRequests: Int,
    ): SpendingQuestionAnswer =
        SpendingQuestionAnswer(
            status = SpendingQuestionAnswerStatus.ANSWERED,
            message = buildAmountMessage(
                subject = "${periodLabel(statistics)} 총지출",
                currentAmount = statistics.current.totalAmount,
                previousAmount = statistics.previous.totalAmount,
                amountChange = statistics.amountChange,
            ),
            intent = query.intent,
            period = query.period,
            payer = query.payer,
            referenceDate = query.referenceDate,
            startDate = statistics.startDate,
            endDateExclusive = statistics.endDateExclusive,
            currentAmount = statistics.current.totalAmount,
            previousAmount = statistics.previous.totalAmount,
            amountChange = statistics.amountChange,
            changeRatePercent = statistics.changeRatePercent,
            evidenceTransactions = evidence,
            remainingRequestsToday = remainingRequests,
        )

    private fun categoryAnswer(
        query: ValidSpendingQuestion,
        statistics: SpendingStatistics,
        evidence: List<SpendingEvidenceTransaction>,
        remainingRequests: Int,
    ): SpendingQuestionAnswer {
        val category = requireNotNull(query.category)
        val comparison = statistics.categoryComparisons.singleOrNull { item -> item.key == category.name }
        val currentAmount = comparison?.currentAmount ?: 0
        val previousAmount = comparison?.previousAmount ?: 0
        val amountChange = currentAmount - previousAmount

        return SpendingQuestionAnswer(
            status = SpendingQuestionAnswerStatus.ANSWERED,
            message = buildAmountMessage(
                subject = "${periodLabel(statistics)} ${category.label}",
                currentAmount = currentAmount,
                previousAmount = previousAmount,
                amountChange = amountChange,
            ),
            intent = query.intent,
            period = query.period,
            payer = query.payer,
            referenceDate = query.referenceDate,
            startDate = statistics.startDate,
            endDateExclusive = statistics.endDateExclusive,
            category = category,
            categoryLabel = category.label,
            currentAmount = currentAmount,
            previousAmount = previousAmount,
            amountChange = amountChange,
            changeRatePercent = comparison?.changeRatePercent,
            evidenceTransactions = evidence,
            remainingRequestsToday = remainingRequests,
        )
    }

    private fun largestTransactionAnswer(
        query: ValidSpendingQuestion,
        statistics: SpendingStatistics,
        evidence: List<SpendingEvidenceTransaction>,
        remainingRequests: Int,
    ): SpendingQuestionAnswer {
        val largest = evidence.firstOrNull()
        val message = if (largest == null) {
            "${periodLabel(statistics)}에는 기록된 지출이 없습니다."
        } else {
            "${periodLabel(statistics)} 가장 큰 지출은 ${largest.merchant} ${formatAmount(largest.amount)}원입니다."
        }

        return SpendingQuestionAnswer(
            status = SpendingQuestionAnswerStatus.ANSWERED,
            message = message,
            intent = query.intent,
            period = query.period,
            payer = query.payer,
            referenceDate = query.referenceDate,
            startDate = statistics.startDate,
            endDateExclusive = statistics.endDateExclusive,
            currentAmount = largest?.amount,
            evidenceTransactions = evidence,
            remainingRequestsToday = remainingRequests,
        )
    }

    private fun requireValidQuery(generated: GeneratedSpendingQuestion): ValidSpendingQuestion {
        val intent = generated.intent
        val period = generated.period
        val payer = generated.payer
        val referenceDate = generated.referenceDate?.let { value ->
            runCatching { LocalDate.parse(value) }.getOrNull()
        }
        val categoryIsValid = when (intent) {
            SpendingQuestionIntent.CATEGORY -> generated.category != null
            SpendingQuestionIntent.TOTAL, SpendingQuestionIntent.LARGEST_TRANSACTION -> generated.category == null
            null -> false
        }

        if (intent == null || period == null || payer == null || referenceDate == null || !categoryIsValid) {
            throw ApiException(
                ErrorCode.AI_ANALYSIS_UNAVAILABLE,
                "가계 질문을 안전한 조회 조건으로 바꾸지 못했습니다. 질문을 조금 더 구체적으로 작성해 주세요.",
            )
        }
        return ValidSpendingQuestion(intent, period, payer, referenceDate, generated.category)
    }

    private fun buildAmountMessage(
        subject: String,
        currentAmount: Long,
        previousAmount: Long,
        amountChange: Long,
    ): String {
        val topic = withTopicParticle(subject)
        val current = "$topic ${formatAmount(currentAmount)}원입니다."
        if (currentAmount == 0L && previousAmount == 0L) return "$topic 기록된 지출이 없습니다."
        if (previousAmount == 0L) return "$current 이전 같은 기간에는 이 지출이 없었습니다."
        if (amountChange == 0L) return "$current 이전 같은 기간과 같은 금액입니다."
        val direction = if (amountChange > 0) "늘었습니다" else "줄었습니다"
        return "$current 이전 같은 기간보다 ${formatAmount(kotlin.math.abs(amountChange))}원 $direction."
    }

    private fun periodLabel(statistics: SpendingStatistics): String =
        when (statistics.period) {
            SpendingPeriod.DAY -> statistics.startDate.format(DAY_FORMATTER)
            SpendingPeriod.WEEK -> {
                val endInclusive = statistics.endDateExclusive.minusDays(1)
                "${statistics.startDate.format(DAY_FORMATTER)}부터 ${endInclusive.format(DAY_FORMATTER)}"
            }
            SpendingPeriod.MONTH -> statistics.startDate.format(MONTH_FORMATTER)
        }

    private fun formatAmount(amount: Long): String = String.format(Locale.KOREA, "%,d", amount)

    private fun withTopicParticle(value: String): String {
        val last = value.last()
        val hasFinalConsonant = last.code in HANGUL_START..HANGUL_END && (last.code - HANGUL_START) % 28 != 0
        return value + if (hasFinalConsonant) "은" else "는"
    }

    private data class ValidSpendingQuestion(
        val intent: SpendingQuestionIntent,
        val period: SpendingPeriod,
        val payer: SpendingPayer,
        val referenceDate: LocalDate,
        val category: TransactionCategory?,
    )

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")
        val logger = LoggerFactory.getLogger(SpendingQuestionService::class.java)
        const val EVIDENCE_LIMIT: Int = 3
        const val HANGUL_START: Int = 0xAC00
        const val HANGUL_END: Int = 0xD7A3
    }
}
