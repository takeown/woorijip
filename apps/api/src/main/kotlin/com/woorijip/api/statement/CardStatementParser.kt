package com.woorijip.api.statement

import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.storedvalue.StoredValueAccountType
import java.time.LocalDate
import java.time.YearMonth

data class CardStatementFile(
    val originalFilename: String?,
    val contentType: String?,
    val bytes: ByteArray,
)

enum class StatementEntryType {
    PURCHASE,
    REVERSAL,
    FEE,
    INSTALLMENT,
}

data class StatementCandidate(
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
    val storedValueAccountType: StoredValueAccountType? = null,
)

data class StatementAdjustment(
    val sourceRow: Int,
    val description: String,
    val amount: Long,
)

data class ParsedCardStatement(
    val cardIssuer: CardIssuer,
    val statementMonth: YearMonth,
    val totalCount: Int,
    val totalBilledAmount: Long,
    val candidates: List<StatementCandidate>,
    val adjustments: List<StatementAdjustment>,
)

interface CardStatementParser {
    fun supports(file: CardStatementFile): Boolean

    fun parse(file: CardStatementFile): ParsedCardStatement
}

class InvalidCardStatementException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
