package com.woorijip.api.statistics

import com.woorijip.api.ai.AiSensitiveInputGuard
import com.woorijip.api.ai.OpenAiProperties
import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneId

enum class SpendingAnalysisStatus {
    ANSWERED,
    NO_DATA,
    UNSUPPORTED,
}

data class SpendingAnalysisEvidence(
    val id: Long,
    val merchant: String,
    val amount: Long,
    val occurredAt: OffsetDateTime,
    val payerLabel: String,
)

data class SpendingAnalysisAnswer(
    val status: SpendingAnalysisStatus,
    val answer: String,
    val evidenceTransactions: List<SpendingAnalysisEvidence>,
    val dataLimited: Boolean,
    val remainingRequestsToday: Int,
)

@Service
class SpendingAnalysisService(
    private val repository: SpendingAnalysisRepository,
    private val generator: SpendingAnalysisGenerator,
    private val sensitiveInputGuard: AiSensitiveInputGuard,
    private val properties: OpenAiProperties,
) {
    fun answer(
        currentUser: CurrentUser,
        question: String,
    ): SpendingAnalysisAnswer {
        sensitiveInputGuard.requireSafe(listOf(question))
        val transactionLimit = properties.analysisTransactionLimit.coerceAtLeast(1)
        val loaded = repository.recentTransactions(currentUser.householdId, transactionLimit + 1)
        val safeTransactions = loaded
            .take(transactionLimit)
            .filter { transaction -> sensitiveInputGuard.isSafeForExternalProcessing(transaction.merchant) }
        val dataLimited = loaded.size > transactionLimit || safeTransactions.size < loaded.take(transactionLimit).size
        val dailyLimit = properties.analysisDailyRequestLimit.coerceAtLeast(1)
        val now = OffsetDateTime.now(SEOUL)

        if (safeTransactions.isEmpty()) {
            val used = repository.dailyRequests(currentUser.householdId, now.toLocalDate())
            return SpendingAnalysisAnswer(
                status = SpendingAnalysisStatus.NO_DATA,
                answer = "분석할 수 있는 거래내역이 아직 없습니다.",
                evidenceTransactions = emptyList(),
                dataLimited = dataLimited,
                remainingRequestsToday = (dailyLimit - used).coerceAtLeast(0),
            )
        }

        val used = repository.consumeDailyRequest(currentUser.householdId, now.toLocalDate(), dailyLimit)
            ?: throw ApiException(
                ErrorCode.AI_USAGE_LIMIT_EXCEEDED,
                "오늘 사용할 수 있는 가계 분석 횟수를 모두 사용했습니다. 내일 다시 질문해 주세요.",
            )
        val transactionsByReference = safeTransactions
            .mapIndexed { index, transaction -> "T${index + 1}" to transaction }
            .toMap()
        val records = transactionsByReference.map { (reference, transaction) ->
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
        val generated = try {
            generator.generate(
                question,
                SpendingAnalysisGenerationContext(
                    currentUserId = currentUser.id,
                    currentTime = now,
                    records = records,
                    dataLimited = dataLimited,
                ),
            )
        } catch (exception: SpendingAnalysisGenerationException) {
            logger.warn("AI 가계 분석 생성 실패: {}", exception.message)
            throw ApiException(
                ErrorCode.AI_ANALYSIS_UNAVAILABLE,
                "가계 분석 답변을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.",
            )
        }

        if (generated.status == GeneratedSpendingAnalysisStatus.UNSUPPORTED) {
            return SpendingAnalysisAnswer(
                status = SpendingAnalysisStatus.UNSUPPORTED,
                answer = "저장된 거래내역에 관한 질문만 답할 수 있습니다.",
                evidenceTransactions = emptyList(),
                dataLimited = dataLimited,
                remainingRequestsToday = dailyLimit - used,
            )
        }

        val answer = generated.answer?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ANSWER_LENGTH }
        val evidence = generated.evidenceReferences
            .distinct()
            .take(MAX_EVIDENCE_COUNT)
            .mapNotNull(transactionsByReference::get)
        if (answer == null || evidence.isEmpty()) {
            logger.warn("AI 가계 분석 응답 검증 실패")
            throw ApiException(
                ErrorCode.AI_ANALYSIS_UNAVAILABLE,
                "가계 분석 답변을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.",
            )
        }

        return SpendingAnalysisAnswer(
            status = SpendingAnalysisStatus.ANSWERED,
            answer = answer,
            evidenceTransactions = evidence.map { transaction ->
                SpendingAnalysisEvidence(
                    id = transaction.id,
                    merchant = transaction.merchant,
                    amount = transaction.amount,
                    occurredAt = transaction.occurredAt,
                    payerLabel = transaction.payerLabel,
                )
            },
            dataLimited = dataLimited,
            remainingRequestsToday = dailyLimit - used,
        )
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val logger = LoggerFactory.getLogger(SpendingAnalysisService::class.java)
        const val MAX_ANSWER_LENGTH: Int = 800
        const val MAX_EVIDENCE_COUNT: Int = 5
    }
}
