package com.woorijip.api.capture

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.transaction.MerchantClassificationRuleService
import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.TransactionDraft
import com.woorijip.api.transaction.TransactionRepository
import com.woorijip.api.transaction.TransactionService
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.text.Normalizer
import java.time.ZoneId

@Service
class CaptureBatchService(
    private val transactions: TransactionRepository,
    private val transactionService: TransactionService,
    private val members: HouseholdMembershipRepository,
    private val rules: MerchantClassificationRuleService,
    private val jdbc: NamedParameterJdbcTemplate,
    private val mapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun check(user: CurrentUser, request: CaptureBatchRequest): List<CaptureCheck> {
        requireMember(user, request.payerId)
        val dates = request.candidates.map { it.occurredAt.atZoneSameInstant(SEOUL).toLocalDate() }
        val existing = transactions.findAllInStatementWindow(user.householdId, request.payerId, request.cardIssuer,
            dates.min().atStartOfDay(SEOUL).toOffsetDateTime(), dates.max().plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime())
        return request.candidates.mapIndexed { index, candidate ->
            val duplicate = existing.any { it.amount == candidate.amount && normalize(it.merchant) == normalize(candidate.merchant) && it.occurredAt.atZoneSameInstant(SEOUL).toLocalDate() == dates[index] } ||
                request.candidates.withIndex().any { (otherIndex, other) -> otherIndex != index && other.amount == candidate.amount && normalize(other.merchant) == normalize(candidate.merchant) && dates[otherIndex] == dates[index] }
            CaptureCheck(duplicate, rules.findRecommendation(user, candidate.merchant)?.category)
        }
    }

    @Transactional
    fun apply(user: CurrentUser, request: CaptureBatchRequest): CaptureApplyResponse {
        requireMember(user, request.payerId)
        val parameters = mapOf("householdId" to user.householdId, "requestId" to request.requestId)
        jdbc.queryForObject("SELECT id FROM households WHERE id = :householdId FOR UPDATE", parameters, Long::class.java)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request)).joinToString("") { "%02x".format(it) }
        val previous = jdbc.query("SELECT fingerprint, saved_count FROM capture_batches WHERE household_id = :householdId AND request_id = :requestId", parameters) { row, _ -> row.getString("fingerprint") to row.getInt("saved_count") }.singleOrNull()
        if (previous != null) {
            if (previous.first != fingerprint) throw ApiException(ErrorCode.TRANSACTION_MODIFIED, "저장 요청의 내용이 달라졌습니다. 다시 확인해 주세요.")
            return CaptureApplyResponse(previous.second)
        }
        val checks = check(user, request)
        if (checks.withIndex().any { (index, check) -> check.duplicate && !request.candidates[index].allowDuplicate }) {
            throw ApiException(ErrorCode.TRANSACTION_MODIFIED, "중복 후보가 있습니다. 다시 확인한 뒤 저장해 주세요.")
        }
        request.candidates.forEach { candidate ->
            transactionService.create(user, TransactionDraft(
                payerId = request.payerId, merchant = candidate.merchant.trim(), description = null,
                amount = candidate.amount, occurredAt = candidate.occurredAt, category = candidate.category,
                paymentMethod = PaymentMethod.CARD, cardIssuer = request.cardIssuer,
            ))
        }
        jdbc.update("INSERT INTO capture_batches (household_id, request_id, fingerprint, saved_count) VALUES (:householdId, :requestId, :fingerprint, :savedCount)", parameters + mapOf("fingerprint" to fingerprint, "savedCount" to request.candidates.size))
        return CaptureApplyResponse(request.candidates.size)
    }

    private fun requireMember(user: CurrentUser, payerId: Long) {
        if (!members.existsByHouseholdIdAndUserId(user.householdId, payerId)) throw ApiException(ErrorCode.PAYER_NOT_IN_HOUSEHOLD, "결제자는 현재 가구의 구성원이어야 합니다.")
    }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
    private companion object { val SEOUL: ZoneId = ZoneId.of("Asia/Seoul") }
}
