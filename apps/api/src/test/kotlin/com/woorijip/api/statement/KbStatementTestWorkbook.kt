package com.woorijip.api.statement

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

object KbStatementTestWorkbook {
    fun create(
        includeRequiredHeader: Boolean = true,
        totalBilledAmount: Long = 109_277,
    ): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("202607_usage")

        if (includeRequiredHeader) {
            sheet.createRow(1).apply {
                text(1, "이용일자")
                text(2, "이용카드")
                text(3, "구분")
                text(4, "이용하신 가맹점")
                text(6, "이용금액")
                text(9, "이번달 결제금액")
            }
            sheet.createRow(2).apply {
                text(1, "이용일자")
                text(2, "이용카드")
                text(3, "구분")
                text(4, "이용하신 가맹점")
                text(6, "이용금액")
                text(7, "할부개월")
                text(8, "회차")
                text(9, "원금")
                text(10, "수수료(이자)")
                text(11, "회차")
                text(12, "원금")
            }
        }

        sheet.createRow(3).apply {
            text(1, "26.06.09")
            text(2, "비자051")
            text(3, "일시불")
            text(4, "세븐일레븐\u00a0테스트점")
            number(6, 2_300)
            number(9, 2_277)
        }
        sheet.createRow(4).apply {
            text(4, "KB\u00a0ALL\u00a0국내할인")
            number(6, -23)
        }
        sheet.createRow(5).apply {
            text(1, "26.05.11")
            text(2, "비자051")
            text(3, "할부")
            text(4, "테스트 할부 가맹점")
            number(6, 600_000)
            number(7, 6)
            number(8, 2)
            number(9, 100_000)
            number(10, 500)
            number(11, 4)
            number(12, 400_000)
        }
        sheet.createRow(6).apply {
            text(4, "무이자혜택금액")
            number(10, -500)
        }
        sheet.createRow(7).apply {
            text(1, "26.05.08")
            text(2, "비자051")
            text(3, "연회비합계금액")
            text(4, "카드별기본연회비")
            number(6, 7_000)
            number(9, 7_000)
        }
        sheet.createRow(8).apply {
            text(1, "합 계 3 건")
            number(9, totalBilledAmount)
        }

        return ByteArrayOutputStream().use { output ->
            workbook.use { it.write(output) }
            output.toByteArray()
        }
    }

    private fun Row.text(
        columnIndex: Int,
        value: String,
    ) {
        createCell(columnIndex).setCellValue(value)
    }

    private fun Row.number(
        columnIndex: Int,
        value: Long,
    ) {
        createCell(columnIndex).setCellValue(value.toDouble())
    }
}
