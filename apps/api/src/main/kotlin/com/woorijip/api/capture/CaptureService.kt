package com.woorijip.api.capture

import com.woorijip.api.ai.AiSensitiveInputGuard
import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import com.woorijip.api.transaction.CardIssuer
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Semaphore

@Service
class CaptureService(
    private val safetyGate: CaptureImageSafetyGate,
    private val generator: CaptureGenerator,
    private val guard: AiSensitiveInputGuard,
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val slot = Semaphore(1)

    fun analyze(user: CurrentUser, image: ByteArray, issuer: CardIssuer, year: Int): GeneratedCapture {
        if (!slot.tryAcquire()) throw ApiException(ErrorCode.AI_USAGE_LIMIT_EXCEEDED, "다른 캡처를 처리하고 있습니다. 잠시 후 다시 시도해 주세요.")
        try {
            safetyGate.validate(image)
            val consumed = jdbc.query("""
                INSERT INTO ai_daily_usage (household_id, usage_date, spending_analysis_requests, capture_requests)
                VALUES (:householdId, :date, 0, 1)
                ON CONFLICT (household_id, usage_date) DO UPDATE
                SET capture_requests = ai_daily_usage.capture_requests + 1
                WHERE ai_daily_usage.capture_requests < 20
                RETURNING capture_requests
            """.trimIndent(), mapOf("householdId" to user.householdId, "date" to LocalDate.now(SEOUL))) { row, _ -> row.getInt(1) }.singleOrNull()
            if (consumed == null) throw ApiException(ErrorCode.AI_USAGE_LIMIT_EXCEEDED, "오늘은 캡처 20장을 모두 분석했습니다. 내일 다시 시도해 주세요.")
            val generated = try { generator.generate(image, user.id, issuer, year) }
            catch (_: Exception) { throw ApiException(ErrorCode.AI_DRAFT_UNAVAILABLE, "캡처 분석에 실패했습니다. 잠시 후 다시 시도해 주세요.") }
            if (generated.entries.size > 30) throw ApiException(ErrorCode.AI_DRAFT_UNAVAILABLE, "한 장의 거래가 너무 많습니다. 나누어 캡처해 주세요.")
            return GeneratedCapture(generated.entries.map { entry ->
                val date = entry.occurredAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }?.takeIf { it.year == year }
                entry.copy(
                    merchant = entry.merchant?.trim()?.takeIf { it.isNotEmpty() && it.length <= 200 && guard.isSafeForExternalProcessing(it) },
                    amount = entry.amount?.takeIf { it in 1..999_999_999 },
                    occurredAt = date?.atZoneSameInstant(SEOUL)?.toOffsetDateTime()?.toString(),
                )
            })
        } finally { slot.release() }
    }
    private companion object { val SEOUL: ZoneId = ZoneId.of("Asia/Seoul") }
}
