package com.woorijip.api.statement

import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class CardStatementFingerprint {
    fun calculate(statement: ParsedCardStatement): String {
        val digest = MessageDigest.getInstance("SHA-256")

        sequence {
            yield(statement.cardIssuer.name)
            yield(statement.statementMonth.toString())
            yield(statement.totalCount.toString())
            yield(statement.totalBilledAmount.toString())
            statement.candidates.sortedBy(StatementCandidate::sourceRow).forEach { candidate ->
                yield(
                    listOf(
                        candidate.sourceRow,
                        candidate.occurredOn,
                        candidate.cardLabel,
                        candidate.merchant,
                        candidate.approvedAmount,
                        candidate.billedAmount,
                        candidate.interestAmount,
                        candidate.type,
                        candidate.installmentMonths,
                        candidate.installmentSequence,
                        candidate.remainingInstallments,
                        candidate.remainingPrincipal,
                        candidate.storedValueAccountType,
                    ).joinToString(FIELD_SEPARATOR),
                )
            }
            statement.adjustments.sortedBy(StatementAdjustment::sourceRow).forEach { adjustment ->
                yield(
                    listOf(
                        adjustment.sourceRow,
                        adjustment.description,
                        adjustment.amount,
                    ).joinToString(FIELD_SEPARATOR),
                )
            }
        }.forEach { value ->
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(RECORD_SEPARATOR)
        }

        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val FIELD_SEPARATOR = "\u001f"
        val RECORD_SEPARATOR = byteArrayOf(0x1e)
    }
}
