package com.woorijip.api.statement

import com.woorijip.api.transaction.CardIssuer
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Component
class KbCardStatementParser : CardStatementParser {
    init {
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO)
        ZipSecureFile.setMaxEntrySize(MAX_ZIP_ENTRY_SIZE_BYTES)
        ZipSecureFile.setMaxTextSize(MAX_EXTRACTED_TEXT_SIZE_BYTES)
    }

    override fun supports(file: CardStatementFile): Boolean =
        file.bytes.size >= XLSX_SIGNATURE.size &&
            XLSX_SIGNATURE.indices.all { index -> file.bytes[index] == XLSX_SIGNATURE[index] }

    override fun parse(file: CardStatementFile): ParsedCardStatement {
        if (!supports(file)) {
            throw InvalidCardStatementException("KB국민카드 XLSX 파일이 아닙니다.")
        }

        return try {
            WorkbookFactory.create(file.bytes.inputStream()).use { workbook ->
                val statementSheet = (0 until workbook.numberOfSheets)
                    .map(workbook::getSheetAt)
                    .firstOrNull { STATEMENT_SHEET_NAME.matches(it.sheetName) }
                    ?: throw InvalidCardStatementException(
                        "KB국민카드 명세서 시트를 찾을 수 없습니다.",
                    )
                parseSheet(statementSheet)
            }
        } catch (exception: InvalidCardStatementException) {
            throw exception
        } catch (exception: Exception) {
            throw InvalidCardStatementException(
                "KB국민카드 XLSX 파일을 읽을 수 없습니다.",
                exception,
            )
        }
    }

    private fun parseSheet(sheet: Sheet): ParsedCardStatement {
        val statementMonth = parseStatementMonth(sheet.sheetName)
        val headerRowIndex = findHeaderRow(sheet)
        val candidates = mutableListOf<StatementCandidate>()
        val adjustments = mutableListOf<StatementAdjustment>()
        var totalCount: Int? = null
        var totalBilledAmount: Long? = null

        for (rowIndex in (headerRowIndex + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val dateText = text(row, DATE_COLUMN)
            val merchant = text(row, MERCHANT_COLUMN)
            val totalMatch = TOTAL_ROW.matchEntire(dateText)

            if (totalMatch != null) {
                totalCount = totalMatch.groupValues[1].toInt()
                totalBilledAmount = amount(row, BILLED_AMOUNT_COLUMN)
                    ?: throw InvalidCardStatementException(
                        "${rowIndex + 1}행의 합계 결제금액이 없습니다.",
                    )
                continue
            }

            val occurredOn = parseDate(dateText)
            if (occurredOn != null) {
                candidates += parseCandidate(row, rowIndex, occurredOn)
                continue
            }

            if (merchant.isNotEmpty()) {
                parseAdjustment(row, rowIndex, merchant)?.let(adjustments::add)
            }
        }

        val expectedCount = totalCount
            ?: throw InvalidCardStatementException("명세서 합계 건수를 찾을 수 없습니다.")
        val expectedBilledAmount = totalBilledAmount
            ?: throw InvalidCardStatementException("명세서 합계 결제금액을 찾을 수 없습니다.")

        if (candidates.size != expectedCount) {
            throw InvalidCardStatementException(
                "명세서 거래 건수가 합계와 일치하지 않습니다.",
            )
        }
        if (candidates.sumOf(StatementCandidate::billedAmount) != expectedBilledAmount) {
            throw InvalidCardStatementException(
                "명세서 결제금액 합계가 일치하지 않습니다.",
            )
        }

        return ParsedCardStatement(
            cardIssuer = CardIssuer.KB_KOOKMIN,
            statementMonth = statementMonth,
            totalCount = expectedCount,
            totalBilledAmount = expectedBilledAmount,
            candidates = candidates,
            adjustments = adjustments,
        )
    }

    private fun findHeaderRow(sheet: Sheet): Int {
        val lastHeaderSearchRow = minOf(sheet.lastRowNum, MAX_HEADER_SEARCH_ROW)
        return (0..lastHeaderSearchRow)
            .lastOrNull { rowIndex ->
                val row = sheet.getRow(rowIndex) ?: return@lastOrNull false
                text(row, DATE_COLUMN) == "이용일자" &&
                    text(row, CARD_COLUMN) == "이용카드" &&
                    text(row, TYPE_COLUMN) == "구분" &&
                    text(row, MERCHANT_COLUMN) == "이용하신 가맹점" &&
                    text(row, APPROVED_AMOUNT_COLUMN) == "이용금액" &&
                    text(row, BILLED_AMOUNT_COLUMN) == "원금"
            }
            ?: throw InvalidCardStatementException(
                "KB국민카드 명세서의 필수 열을 찾을 수 없습니다.",
            )
    }

    private fun parseCandidate(
        row: Row,
        rowIndex: Int,
        occurredOn: LocalDate,
    ): StatementCandidate {
        val cardLabel = text(row, CARD_COLUMN)
        val merchant = text(row, MERCHANT_COLUMN)
        val statementType = text(row, TYPE_COLUMN)
        val approvedAmount = amount(row, APPROVED_AMOUNT_COLUMN)
            ?: throw InvalidCardStatementException(
                "${rowIndex + 1}행의 이용금액이 없습니다.",
            )
        val billedAmount = amount(row, BILLED_AMOUNT_COLUMN) ?: 0L

        if (cardLabel.isEmpty() || merchant.isEmpty()) {
            throw InvalidCardStatementException(
                "${rowIndex + 1}행의 카드 또는 가맹점 정보가 없습니다.",
            )
        }

        val type = when {
            approvedAmount < 0 -> StatementEntryType.REVERSAL
            statementType == "일시불" -> StatementEntryType.PURCHASE
            statementType == "할부" -> StatementEntryType.INSTALLMENT
            statementType == "연회비합계금액" -> StatementEntryType.FEE
            else -> throw InvalidCardStatementException(
                "${rowIndex + 1}행의 거래 구분을 지원하지 않습니다.",
            )
        }

        return StatementCandidate(
            sourceRow = rowIndex + 1,
            occurredOn = occurredOn,
            cardLabel = cardLabel,
            merchant = merchant,
            approvedAmount = approvedAmount,
            billedAmount = billedAmount,
            interestAmount = amount(row, INTEREST_AMOUNT_COLUMN) ?: 0L,
            type = type,
            installmentMonths = integer(row, INSTALLMENT_MONTHS_COLUMN),
            installmentSequence = integer(row, INSTALLMENT_SEQUENCE_COLUMN),
            remainingInstallments = integer(row, REMAINING_INSTALLMENTS_COLUMN),
            remainingPrincipal = amount(row, REMAINING_PRINCIPAL_COLUMN),
        )
    }

    private fun parseAdjustment(
        row: Row,
        rowIndex: Int,
        merchant: String,
    ): StatementAdjustment? {
        val adjustmentAmount = listOf(
            amount(row, APPROVED_AMOUNT_COLUMN),
            amount(row, INTEREST_AMOUNT_COLUMN),
            amount(row, BILLED_AMOUNT_COLUMN),
        ).firstOrNull { amount -> amount != null && amount != 0L } ?: return null

        return StatementAdjustment(
            sourceRow = rowIndex + 1,
            description = merchant,
            amount = adjustmentAmount,
        )
    }

    private fun parseStatementMonth(sheetName: String): YearMonth {
        val match = STATEMENT_SHEET_NAME.matchEntire(sheetName)
            ?: throw InvalidCardStatementException("명세서 월을 확인할 수 없습니다.")
        return try {
            YearMonth.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        } catch (_: DateTimeException) {
            throw InvalidCardStatementException("명세서 월이 올바르지 않습니다.")
        }
    }

    private fun parseDate(value: String): LocalDate? {
        val match = DATE_VALUE.matchEntire(value) ?: return null
        return try {
            LocalDate.of(
                2000 + match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            )
        } catch (_: DateTimeException) {
            throw InvalidCardStatementException("올바르지 않은 이용일자가 있습니다.")
        }
    }

    private fun text(
        row: Row,
        columnIndex: Int,
    ): String =
        formatter
            .formatCellValue(row.getCell(columnIndex))
            .replace('\u00a0', ' ')
            .trim()
            .replace(WHITESPACE, " ")

    private fun amount(
        row: Row,
        columnIndex: Int,
    ): Long? {
        val value = text(row, columnIndex)
        if (value.isEmpty()) {
            return null
        }
        return value
            .replace(",", "")
            .toLongOrNull()
            ?: throw InvalidCardStatementException(
                "${row.rowNum + 1}행의 금액 형식이 올바르지 않습니다.",
            )
    }

    private fun integer(
        row: Row,
        columnIndex: Int,
    ): Int? {
        val value = text(row, columnIndex)
        if (value.isEmpty()) {
            return null
        }
        return value.toIntOrNull()
            ?: throw InvalidCardStatementException(
                "${row.rowNum + 1}행의 할부 정보가 올바르지 않습니다.",
            )
    }

    private companion object {
        val formatter = DataFormatter(Locale.KOREA)
        const val MIN_INFLATE_RATIO = 0.005
        const val MAX_ZIP_ENTRY_SIZE_BYTES = 16L * 1024 * 1024
        const val MAX_EXTRACTED_TEXT_SIZE_BYTES = 16L * 1024 * 1024
        val XLSX_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val STATEMENT_SHEET_NAME = Regex("""(\d{4})(\d{2})_usage""")
        val DATE_VALUE = Regex("""(\d{2})\.(\d{2})\.(\d{2})""")
        val TOTAL_ROW = Regex("""합\s*계\s*(\d+)\s*건""")
        val WHITESPACE = Regex("""\s+""")
        const val MAX_HEADER_SEARCH_ROW = 20
        const val DATE_COLUMN = 1
        const val CARD_COLUMN = 2
        const val TYPE_COLUMN = 3
        const val MERCHANT_COLUMN = 4
        const val APPROVED_AMOUNT_COLUMN = 6
        const val INSTALLMENT_MONTHS_COLUMN = 7
        const val INSTALLMENT_SEQUENCE_COLUMN = 8
        const val BILLED_AMOUNT_COLUMN = 9
        const val INTEREST_AMOUNT_COLUMN = 10
        const val REMAINING_INSTALLMENTS_COLUMN = 11
        const val REMAINING_PRINCIPAL_COLUMN = 12
    }
}
