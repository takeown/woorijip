package com.woorijip.api.capture

import com.woorijip.api.ai.AiSensitiveInputGuard
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

internal fun invalidCapture(message: String): Nothing = throw ApiException(ErrorCode.INVALID_REQUEST, message)

data class CaptureWord(val text: String, val confidence: Double, val x: Int, val y: Int, val width: Int, val height: Int)

fun interface CaptureTextReader {
    fun read(image: BufferedImage): List<CaptureWord>
}

@Component
class TesseractCaptureTextReader : CaptureTextReader {
    override fun read(image: BufferedImage): List<CaptureWord> {
        val directory = Files.createTempDirectory("woorijip-capture-")
        val input = directory.resolve("input.png")
        val output = directory.resolve("output")
        var process: Process? = null
        try {
            ImageIO.write(image, "png", input.toFile())
            process = ProcessBuilder("tesseract", input.toString(), output.toString(), "-l", "kor+eng", "--psm", "11", "tsv")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .apply { environment()["OMP_THREAD_LIMIT"] = "1" }
                .start()
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                invalidCapture("이미지를 안전하게 읽지 못했습니다. 선명한 캡처로 다시 시도해 주세요.")
            }
            val tsv = output.resolveSibling("output.tsv")
            if (!Files.exists(tsv) || Files.size(tsv) > 2_000_000) invalidCapture("캡처에 글자가 너무 많습니다.")
            return Files.readAllLines(tsv).drop(1).mapNotNull { line ->
                val fields = line.split('\t', limit = 12)
                if (fields.size != 12 || fields[0] != "5" || fields[11].isBlank()) return@mapNotNull null
                CaptureWord(fields[11], fields[10].toDouble(), fields[6].toInt(), fields[7].toInt(), fields[8].toInt(), fields[9].toInt())
            }.also { if (it.size > 2000) invalidCapture("캡처에 글자가 너무 많습니다.") }
        } catch (exception: ApiException) {
            throw exception
        } catch (_: Exception) {
            throw ApiException(ErrorCode.AI_DRAFT_UNAVAILABLE, "이미지 안전 검사를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.")
        } finally {
            process?.takeIf { it.isAlive }?.let {
                it.destroyForcibly()
                it.waitFor(5, TimeUnit.SECONDS)
            }
            Files.list(directory).use { paths -> paths.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(directory)
        }
    }
}

@Component
class CaptureImageSafetyGate(
    private val reader: CaptureTextReader,
    private val guard: AiSensitiveInputGuard,
) {
    fun validate(bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) {
            invalidCapture("PNG·JPEG 캡처를 장당 4MB 이하로 선택해 주세요.")
        }
        val image = decode(bytes)
        try {
            val text = reader.read(image)
                .filter { it.width > 0 && it.height > 0 && it.x >= 0 && it.y >= 0 && it.x + it.width <= image.width && it.y + it.height <= image.height }
                .joinToString(" ") { it.text }
            if (
                text.isBlank() ||
                !guard.isSafeForExternalProcessing(text) ||
                PERSONAL_LABEL.containsMatchIn(text) ||
                LONG_UNMASKED_NUMBER.containsMatchIn(text)
            ) {
                invalidCapture("전체 카드번호·계좌번호·이름·이메일·전화번호가 보이지 않는 카드 내역 캡처를 선택해 주세요.")
            }
        } finally {
            image.flush()
        }
    }

    private fun decode(bytes: ByteArray): BufferedImage = try {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) invalidCapture("PNG·JPEG 캡처만 지원합니다.")
            val decoder = readers.next()
            try {
                if (decoder.formatName.lowercase() !in setOf("png", "jpeg", "jpg")) invalidCapture("PNG·JPEG 캡처만 지원합니다.")
                decoder.input = stream
                val width = decoder.getWidth(0)
                val height = decoder.getHeight(0)
                if (width !in 200..4000 || height !in 200..6000 || width.toLong() * height > 8_000_000) {
                    invalidCapture("캡처 크기가 너무 크거나 작습니다. 일반 화면 캡처를 선택해 주세요.")
                }
                decoder.read(0)
            } finally {
                decoder.dispose()
            }
        }
    } catch (exception: ApiException) {
        throw exception
    } catch (_: Exception) {
        invalidCapture("손상된 이미지입니다. 다시 캡처해 주세요.")
    }

    private companion object {
        val PERSONAL_LABEL = Regex("카드번호|계좌번호|성명|이름|고객명|회원명|전화번호|이메일|주민등록")
        val LONG_UNMASKED_NUMBER = Regex("(?<!\\d)(?:\\d[ -]?){8,19}(?!\\d)")
    }
}
