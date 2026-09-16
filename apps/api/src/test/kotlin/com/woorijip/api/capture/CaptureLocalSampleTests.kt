package com.woorijip.api.capture

import com.woorijip.api.ai.AiSensitiveInputGuard
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

@EnabledIfEnvironmentVariable(named = "CAPTURE_SAMPLE_DIR", matches = ".+")
class CaptureLocalSampleTests {
    @Test
    fun acceptsLocallySuppliedMaskedSamplesWithoutCallingExternalService() {
        val directory = Path.of(System.getenv("CAPTURE_SAMPLE_DIR"))
        val gate = CaptureImageSafetyGate(TesseractCaptureTextReader(), AiSensitiveInputGuard())
        for (issuer in listOf("shinhan", "kb", "hyundai")) {
            try {
                gate.validate(Files.readAllBytes(directory.resolve(issuer + ".png")))
            } catch (exception: Exception) {
                throw AssertionError(issuer + " sample failed", exception)
            }
        }
    }
}
