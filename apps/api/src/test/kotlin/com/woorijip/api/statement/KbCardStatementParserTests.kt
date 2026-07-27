package com.woorijip.api.statement

import com.woorijip.api.transaction.CardIssuer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KbCardStatementParserTests {
    private val parser = KbCardStatementParser()

    @Test
    fun `normalizes KB purchases installments fees and adjustments`() {
        val result = parser.parse(statementFile())

        assertEquals(CardIssuer.KB_KOOKMIN, result.cardIssuer)
        assertEquals(YearMonth.of(2026, 7), result.statementMonth)
        assertEquals(3, result.totalCount)
        assertEquals(109_277, result.totalBilledAmount)
        assertEquals(3, result.candidates.size)
        assertEquals(2, result.adjustments.size)

        val purchase = result.candidates[0]
        assertEquals(LocalDate.of(2026, 6, 9), purchase.occurredOn)
        assertEquals("세븐일레븐 테스트점", purchase.merchant)
        assertEquals(2_300, purchase.approvedAmount)
        assertEquals(2_277, purchase.billedAmount)
        assertEquals(StatementEntryType.PURCHASE, purchase.type)

        val installment = result.candidates[1]
        assertEquals(StatementEntryType.INSTALLMENT, installment.type)
        assertEquals(6, installment.installmentMonths)
        assertEquals(2, installment.installmentSequence)
        assertEquals(500, installment.interestAmount)
        assertEquals(4, installment.remainingInstallments)
        assertEquals(400_000, installment.remainingPrincipal)

        assertEquals(StatementEntryType.FEE, result.candidates[2].type)
        assertEquals(-23, result.adjustments[0].amount)
        assertEquals(-500, result.adjustments[1].amount)
    }

    @Test
    fun `rejects files without KB required headers`() {
        assertFailsWith<InvalidCardStatementException> {
            parser.parse(
                statementFile(
                    KbStatementTestWorkbook.create(includeRequiredHeader = false),
                ),
            )
        }
    }

    @Test
    fun `rejects statements whose billed total does not reconcile`() {
        assertFailsWith<InvalidCardStatementException> {
            parser.parse(
                statementFile(
                    KbStatementTestWorkbook.create(totalBilledAmount = 1),
                ),
            )
        }
    }

    private fun statementFile(
        bytes: ByteArray = KbStatementTestWorkbook.create(),
    ) = CardStatementFile(
        originalFilename = "kb-statement.xlsx",
        contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        bytes = bytes,
    )
}
