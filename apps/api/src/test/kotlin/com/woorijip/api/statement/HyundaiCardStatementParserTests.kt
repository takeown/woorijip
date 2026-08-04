package com.woorijip.api.statement

import com.woorijip.api.storedvalue.StoredValueAutomationKey
import com.woorijip.api.transaction.CardIssuer
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HyundaiCardStatementParserTests {
    private val parser = HyundaiCardStatementParser()

    @Test
    fun `pairs an adjacent Onnuri adjustment with its merchant purchase`() {
        val statement = parser.parse(statementFile(validHtml()))

        assertEquals(CardIssuer.HYUNDAI, statement.cardIssuer)
        assertEquals(YearMonth.of(2026, 7), statement.statementMonth)
        assertEquals(2, statement.totalCount)
        assertEquals(1_000, statement.totalBilledAmount)
        assertEquals(3, statement.candidates.size)
        assertEquals(1, statement.adjustments.size)

        val onnuri = statement.candidates.first()
        assertEquals(LocalDate.of(2026, 6, 5), onnuri.occurredOn)
        assertEquals("GS25 테스트점", onnuri.merchant)
        assertEquals(2_400, onnuri.approvedAmount)
        assertEquals(0, onnuri.billedAmount)
        assertEquals(StoredValueAutomationKey.ONNURI_GIFT_CERTIFICATE, onnuri.storedValueAccountType)
        assertEquals(-2_400, statement.adjustments.single().amount)
        assertEquals(StatementEntryType.REVERSAL, statement.candidates.last().type)
    }

    @Test
    fun `rejects an Onnuri adjustment without an adjacent matching purchase`() {
        val invalid = validHtml().replace(
            "<td>2,400</td><td></td><td>1.0%</td><td>24</td><td>0</td>",
            "<td>2,500</td><td></td><td>1.0%</td><td>25</td><td>0</td>",
        )

        assertFailsWith<InvalidCardStatementException> {
            parser.parse(statementFile(invalid))
        }
    }

    @Test
    fun `rejects a non Hyundai html file`() {
        assertFailsWith<InvalidCardStatementException> {
            parser.parse(statementFile("<html><table><tr><td>다른 문서</td></tr></table></html>"))
        }
    }

    private fun statementFile(html: String) =
        CardStatementFile(
            originalFilename = "hyundaicard.xls",
            contentType = "application/vnd.ms-excel",
            bytes = html.toByteArray(),
        )

    private fun validHtml(): String =
        """
        <html><body><table>
          <tr><th colspan="10">2026년 07월 이용대금명세서</th></tr>
          <tr>
            <th>이용일</th><th>이용카드</th><th>이용가맹점</th><th>이용금액</th>
            <th>할부/회차</th><th>적립/할인율(%)</th><th>예상적립/할인</th>
            <th>결제원금</th><th>결제후잔액</th><th>수수료(이자)</th>
          </tr>
          <tr><td>2026년 06월 05일</td><td>본인 테스트 현대카드</td><td>GS25 테스트점</td><td>2,400</td><td></td><td>1.0%</td><td>24</td><td>0</td><td>0</td><td>0</td></tr>
          <tr><td>2026년 06월 05일</td><td>본인 테스트 현대카드</td><td>온누리상품권사용(청구할인)</td><td>0</td><td></td><td>0%</td><td>-2,400</td><td>0</td><td>0</td><td>0</td></tr>
          <tr><td>2026년 06월 07일</td><td>본인 테스트 현대카드</td><td>일반 가맹점</td><td>1,000</td><td></td><td>1.0%</td><td>10</td><td>1,000</td><td>0</td><td>0</td></tr>
          <tr><td>2026년 06월 08일</td><td>본인 테스트 현대카드</td><td>취소 가맹점</td><td>-500</td><td></td><td>0%</td><td>0</td><td>0</td><td>0</td><td>0</td></tr>
          <tr><td>-</td><td></td><td>청 구 할 인 소계 1 건</td><td>0</td><td></td><td></td><td>-2,400</td><td>0</td><td>0</td><td>0</td></tr>
          <tr><td>-</td><td></td><td>총 합계 2 건</td><td>0</td><td></td><td></td><td>0</td><td>1,000</td><td>0</td><td>0</td></tr>
        </table></body></html>
        """.trimIndent()
}
