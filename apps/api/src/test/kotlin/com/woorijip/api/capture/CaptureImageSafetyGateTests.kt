package com.woorijip.api.capture

import com.woorijip.api.ai.AiSensitiveInputGuard
import com.woorijip.api.error.ApiException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CaptureImageSafetyGateTests {
    @Test
    fun acceptsMaskedCardIdentifiers() {
        safetyGate(
            listOf(
                word("테스트마트", 20, 200),
                word("12,000원", 350, 200),
                word("본인", 20, 240),
                word("351*", 80, 240),
                word("(0051)", 160, 240),
            ),
        ).validate(imageBytes())
    }

    @Test
    fun rejectsSensitiveTextBeforeExternalProcessing() {
        for (text in listOf(
            "4111111111111111",
            "계좌 1234567890",
            "010-1234-5678",
            "test@example.com",
            "고객명 홍길동",
            "1234567890",
        )) {
            assertFailsWith<ApiException>(text) {
                safetyGate(listOf(word("테스트마트", 20, 200), word(text, 20, 240))).validate(imageBytes())
            }
        }
    }

    @Test
    fun rejectsEmptyOversizedCorruptAndUnsupportedFilesBeforeOcr() {
        val gate = safetyGate(emptyList())
        for (bytes in listOf(byteArrayOf(), ByteArray(4 * 1024 * 1024 + 1), "not an image".toByteArray(), imageBytes("gif"))) {
            assertFailsWith<ApiException> { gate.validate(bytes) }
        }
    }

    private fun safetyGate(words: List<CaptureWord>) = CaptureImageSafetyGate(
        CaptureTextReader { words },
        AiSensitiveInputGuard(),
    )

    companion object {
        fun word(text: String, x: Int, y: Int) = CaptureWord(text, 95.0, x, y, 100, 20)

        fun imageBytes(format: String = "png"): ByteArray {
            val image = BufferedImage(500, 400, BufferedImage.TYPE_INT_RGB)
            return ByteArrayOutputStream().use { out ->
                ImageIO.write(image, format, out)
                out.toByteArray()
            }
        }
    }
}
