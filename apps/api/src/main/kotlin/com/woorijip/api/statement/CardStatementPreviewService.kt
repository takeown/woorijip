package com.woorijip.api.statement

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.transaction.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.ZoneId

data class CardStatementPreview(
    val statement: ParsedCardStatement,
    val matches: List<StatementMatch>,
)

@Service
class CardStatementPreviewService(
    private val parsers: List<CardStatementParser>,
    private val transactionRepository: TransactionRepository,
    private val matcher: CardStatementMatcher,
) {
    @Transactional(readOnly = true)
    fun preview(
        currentUser: CurrentUser,
        file: MultipartFile,
    ): CardStatementPreview {
        if (file.isEmpty) {
            throw InvalidCardStatementException("명세서 파일을 선택해 주세요.")
        }
        if (file.size > MAX_FILE_SIZE_BYTES) {
            throw InvalidCardStatementException("명세서 파일은 2MB 이하여야 합니다.")
        }

        val statementFile = CardStatementFile(
            originalFilename = file.originalFilename,
            contentType = file.contentType,
            bytes = file.bytes,
        )
        val parser = parsers.firstOrNull { it.supports(statementFile) }
            ?: throw InvalidCardStatementException("지원하지 않는 명세서 파일 형식입니다.")
        val statement = parser.parse(statementFile)
        if (statement.candidates.isEmpty()) {
            return CardStatementPreview(statement = statement, matches = emptyList())
        }
        val firstDate = statement.candidates.minOf(StatementCandidate::occurredOn)
        val lastDate = statement.candidates.maxOf(StatementCandidate::occurredOn)
        val transactions = transactionRepository
            .findAllByHouseholdIdAndPayerIdAndCardIssuerAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                householdId = currentUser.householdId,
                payerId = currentUser.id,
                cardIssuer = statement.cardIssuer,
                occurredAtFrom = firstDate.atStartOfDay(SEOUL_ZONE_ID).toOffsetDateTime(),
                occurredAtTo = lastDate.plusDays(1).atStartOfDay(SEOUL_ZONE_ID).toOffsetDateTime(),
            )

        return CardStatementPreview(
            statement = statement,
            matches = matcher.match(statement.candidates, transactions),
        )
    }

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024
        val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
