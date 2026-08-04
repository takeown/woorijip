package com.woorijip.api.statement

import com.woorijip.api.storedvalue.StoredValueAutomationKey
import com.woorijip.api.transaction.CardIssuer
import org.springframework.stereotype.Component
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

@Component
class HyundaiCardStatementParser : CardStatementParser {
    override fun supports(file: CardStatementFile): Boolean =
        decode(file.bytes)?.let { html ->
            html.contains("<html", ignoreCase = true) &&
                html.contains("이용가맹점") &&
                html.contains("이용대금명세서")
        } == true

    override fun parse(file: CardStatementFile): ParsedCardStatement {
        val html = decode(file.bytes)
            ?: throw InvalidCardStatementException("현대카드 XLS 파일의 문자 형식이 올바르지 않습니다.")
        if (!supports(file)) {
            throw InvalidCardStatementException("현대카드 HTML 기반 XLS 파일이 아닙니다.")
        }

        return try {
            parseRows(HtmlTableReader().read(html))
        } catch (exception: InvalidCardStatementException) {
            throw exception
        } catch (exception: Exception) {
            throw InvalidCardStatementException("현대카드 XLS 파일을 읽을 수 없습니다.", exception)
        }
    }

    private fun parseRows(rows: List<HtmlRow>): ParsedCardStatement {
        val statementMonth = rows.firstNotNullOfOrNull { row ->
            row.cells.firstNotNullOfOrNull { cell -> parseStatementMonth(cell) }
        } ?: throw InvalidCardStatementException("현대카드 명세서 월을 찾을 수 없습니다.")
        val headerIndex = rows.indexOfFirst { row -> row.cells == REQUIRED_HEADERS }
        if (headerIndex < 0) {
            throw InvalidCardStatementException("현대카드 명세서의 필수 열을 찾을 수 없습니다.")
        }

        val candidates = mutableListOf<StatementCandidate>()
        val adjustments = mutableListOf<StatementAdjustment>()
        var totalCount: Int? = null
        var totalBilledAmount: Long? = null
        var adjustmentCount: Int? = null

        rows.drop(headerIndex + 1).forEach { row ->
            if (row.cells.size != REQUIRED_HEADERS.size) return@forEach
            val totalMatch = TOTAL_ROW.matchEntire(row.cells[2])
            if (totalMatch != null) {
                totalCount = totalMatch.groupValues[1].toInt()
                totalBilledAmount = amount(row, BILLED_AMOUNT_COLUMN)
                return@forEach
            }
            ADJUSTMENT_SUBTOTAL_ROW.matchEntire(row.cells[2])?.let { match ->
                adjustmentCount = match.groupValues[1].toInt()
                return@forEach
            }

            val occurredOn = parseDate(row.cells[DATE_COLUMN]) ?: return@forEach
            val merchant = row.cells[MERCHANT_COLUMN]
            if (merchant == ONNURI_ADJUSTMENT_DESCRIPTION) {
                val adjustmentAmount = amount(row, DISCOUNT_AMOUNT_COLUMN)
                if (adjustmentAmount >= 0) {
                    throw InvalidCardStatementException("온누리상품권 조정 금액 형식이 올바르지 않습니다.")
                }
                val previousIndex = candidates.lastIndex
                val previous = candidates.getOrNull(previousIndex)
                if (
                    previous == null ||
                    previous.occurredOn != occurredOn ||
                    previous.cardLabel != row.cells[CARD_COLUMN] ||
                    previous.approvedAmount != -adjustmentAmount ||
                    previous.billedAmount != 0L
                ) {
                    throw InvalidCardStatementException("온누리상품권 사용 행과 대응하는 가맹점 결제를 찾을 수 없습니다.")
                }
                candidates[previousIndex] = previous.copy(
                    storedValueAccountType = StoredValueAutomationKey.ONNURI_GIFT_CERTIFICATE,
                )
                adjustments += StatementAdjustment(row.sourceRow, merchant, adjustmentAmount)
                return@forEach
            }

            candidates += parseCandidate(row, occurredOn)
        }

        val expectedCount = totalCount
            ?: throw InvalidCardStatementException("현대카드 명세서 합계 건수를 찾을 수 없습니다.")
        val expectedBilledAmount = totalBilledAmount
            ?: throw InvalidCardStatementException("현대카드 명세서 합계 결제원금을 찾을 수 없습니다.")
        if (candidates.count { candidate -> candidate.approvedAmount > 0 } != expectedCount) {
            throw InvalidCardStatementException("현대카드 명세서 거래 건수가 합계와 일치하지 않습니다.")
        }
        if (candidates.sumOf(StatementCandidate::billedAmount) != expectedBilledAmount) {
            throw InvalidCardStatementException("현대카드 명세서 결제원금 합계가 일치하지 않습니다.")
        }
        if (adjustmentCount != null && adjustments.size != adjustmentCount) {
            throw InvalidCardStatementException("현대카드 명세서 청구할인 건수가 소계와 일치하지 않습니다.")
        }

        return ParsedCardStatement(
            cardIssuer = CardIssuer.HYUNDAI,
            statementMonth = statementMonth,
            totalCount = expectedCount,
            totalBilledAmount = expectedBilledAmount,
            candidates = candidates,
            adjustments = adjustments,
        )
    }

    private fun parseCandidate(row: HtmlRow, occurredOn: LocalDate): StatementCandidate {
        val cardLabel = row.cells[CARD_COLUMN]
        val merchant = row.cells[MERCHANT_COLUMN]
        if (cardLabel.isBlank() || cardLabel.length > 100 || merchant.isBlank() || merchant.length > 200) {
            throw InvalidCardStatementException("${row.sourceRow}행의 카드 또는 가맹점 정보가 올바르지 않습니다.")
        }
        val approvedAmount = amount(row, APPROVED_AMOUNT_COLUMN)
        val installmentNumbers = NUMBER.findAll(row.cells[INSTALLMENT_COLUMN]).map { it.value.toInt() }.toList()
        val type = when {
            approvedAmount < 0 -> StatementEntryType.REVERSAL
            merchant.contains("연회비") -> StatementEntryType.FEE
            installmentNumbers.isNotEmpty() -> StatementEntryType.INSTALLMENT
            else -> StatementEntryType.PURCHASE
        }
        return StatementCandidate(
            sourceRow = row.sourceRow,
            occurredOn = occurredOn,
            cardLabel = cardLabel,
            merchant = merchant,
            approvedAmount = approvedAmount,
            billedAmount = amount(row, BILLED_AMOUNT_COLUMN),
            interestAmount = amount(row, INTEREST_AMOUNT_COLUMN),
            type = type,
            installmentMonths = installmentNumbers.firstOrNull(),
            installmentSequence = installmentNumbers.getOrNull(1),
            remainingInstallments = null,
            remainingPrincipal = null,
        )
    }

    private fun amount(row: HtmlRow, column: Int): Long =
        row.cells[column]
            .replace(",", "")
            .trim()
            .takeIf(String::isNotEmpty)
            ?.toLongOrNull()
            ?: throw InvalidCardStatementException("${row.sourceRow}행의 금액 형식이 올바르지 않습니다.")

    private fun parseStatementMonth(value: String): YearMonth? {
        val match = STATEMENT_MONTH.find(value) ?: return null
        return try {
            YearMonth.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        } catch (_: DateTimeException) {
            throw InvalidCardStatementException("현대카드 명세서 월이 올바르지 않습니다.")
        }
    }

    private fun parseDate(value: String): LocalDate? {
        val match = DATE_VALUE.matchEntire(value) ?: return null
        return try {
            LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
        } catch (_: DateTimeException) {
            throw InvalidCardStatementException("올바르지 않은 현대카드 이용일이 있습니다.")
        }
    }

    private fun decode(bytes: ByteArray): String? =
        runCatching {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()

    private data class HtmlRow(
        val sourceRow: Int,
        val cells: List<String>,
    )

    private class HtmlTableReader : HTMLEditorKit.ParserCallback() {
        private val rows = mutableListOf<HtmlRow>()
        private var currentRow: MutableList<String>? = null
        private var currentCell: StringBuilder? = null

        fun read(html: String): List<HtmlRow> {
            ParserDelegator().parse(StringReader(html), this, true)
            return rows
        }

        override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, position: Int) {
            when (tag) {
                HTML.Tag.TR -> {
                    if (rows.size >= MAX_ROWS) {
                        throw InvalidCardStatementException("현대카드 명세서의 행이 너무 많습니다.")
                    }
                    currentRow = mutableListOf()
                }
                HTML.Tag.TD,
                HTML.Tag.TH,
                -> if (currentRow != null) currentCell = StringBuilder()
            }
        }

        override fun handleText(data: CharArray, position: Int) {
            currentCell?.append(data)
            if ((currentCell?.length ?: 0) > MAX_CELL_LENGTH) {
                throw InvalidCardStatementException("현대카드 명세서의 셀 내용이 너무 깁니다.")
            }
        }

        override fun handleEndTag(tag: HTML.Tag, position: Int) {
            when (tag) {
                HTML.Tag.TD,
                HTML.Tag.TH,
                -> {
                    currentCell?.let { cell ->
                        currentRow?.add(cell.toString().replace(WHITESPACE, " ").trim())
                    }
                    currentCell = null
                }
                HTML.Tag.TR -> {
                    currentRow?.takeIf(List<String>::isNotEmpty)?.let { cells ->
                        rows += HtmlRow(rows.size + 1, cells.toList())
                    }
                    currentRow = null
                    currentCell = null
                }
            }
        }
    }

    private companion object {
        val REQUIRED_HEADERS = listOf(
            "이용일",
            "이용카드",
            "이용가맹점",
            "이용금액",
            "할부/회차",
            "적립/할인율(%)",
            "예상적립/할인",
            "결제원금",
            "결제후잔액",
            "수수료(이자)",
        )
        val STATEMENT_MONTH = Regex("""(\d{4})년\s*(\d{2})월\s*이용대금명세서""")
        val DATE_VALUE = Regex("""(\d{4})년\s*(\d{2})월\s*(\d{2})일""")
        val TOTAL_ROW = Regex("""총\s*합계\s*(\d+)\s*건""")
        val ADJUSTMENT_SUBTOTAL_ROW = Regex("""청\s*구\s*할\s*인\s*소계\s*(\d+)\s*건""")
        val NUMBER = Regex("""\d+""")
        val WHITESPACE = Regex("""\s+""")
        const val ONNURI_ADJUSTMENT_DESCRIPTION = "온누리상품권사용(청구할인)"
        const val MAX_ROWS = 5_000
        const val MAX_CELL_LENGTH = 1_000
        const val DATE_COLUMN = 0
        const val CARD_COLUMN = 1
        const val MERCHANT_COLUMN = 2
        const val APPROVED_AMOUNT_COLUMN = 3
        const val INSTALLMENT_COLUMN = 4
        const val DISCOUNT_AMOUNT_COLUMN = 6
        const val BILLED_AMOUNT_COLUMN = 7
        const val INTEREST_AMOUNT_COLUMN = 9
    }
}
