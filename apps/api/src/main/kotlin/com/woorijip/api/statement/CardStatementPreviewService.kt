package com.woorijip.api.statement

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class CardStatementPreviewService(
    private val parsers: List<CardStatementParser>,
) {
    fun preview(file: MultipartFile): ParsedCardStatement {
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
        return parser.parse(statementFile)
    }

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024
    }
}
