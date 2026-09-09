package com.woorijip.api.capture

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.auth.TestOidcUsers
import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.privacy.PrivacyConsentService
import com.woorijip.api.transaction.TransactionCategory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(properties = ["app.auth.allowed-google-emails=first@example.com,second@example.com", "app.auth.bootstrap-household-name=테스트 우리집"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, CaptureTestConfiguration::class)
@Transactional
class CaptureControllerTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val accounts: GoogleAccountService,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val generator: StubCaptureGenerator,
    @Autowired private val privacyConsentService: PrivacyConsentService,
) {

    @Test
    fun `applies a batch once and marks later duplicates`() {
        val user = provisionAllowed()
        val body = request(user.id)
        repeat(2) { post("apply", body).andExpect { status { isOk() }; jsonPath("$.savedCount") { value(1) } } }
        post("check", request(user.id)).andExpect { status { isOk() }; jsonPath("$[0].duplicate") { value(true) } }
        post("apply", request(user.id)).andExpect { status { isConflict() } }
        post("apply", body.replace("12000", "13000")).andExpect { status { isConflict() } }
    }

    @Test
    fun `requires authentication csrf household ownership and valid nested candidates`() {
        val user = provisionAllowed()
        mvc.post("/transaction-captures/apply") { contentType = MediaType.APPLICATION_JSON; content = request(user.id); with(csrf()) }.andExpect { status { isUnauthorized() } }
        mvc.post("/transaction-captures/apply") { contentType = MediaType.APPLICATION_JSON; content = request(user.id); with(oidcLogin().oidcUser(TestOidcUsers.allowed())) }.andExpect { status { isForbidden() } }
        post("apply", request(Long.MAX_VALUE)).andExpect { status { isBadRequest() } }
        post("check", request(Long.MAX_VALUE)).andExpect { status { isBadRequest() } }
        post("apply", request(user.id).replace("12000", "-1")).andExpect { status { isBadRequest() } }
        post("apply", request(user.id).replace("테스트마트", " ")).andExpect { status { isBadRequest() } }
        post("apply", mapper.writeValueAsString(mapOf("requestId" to UUID.randomUUID(), "payerId" to user.id, "cardIssuer" to "SHINHAN", "candidates" to emptyList<Any>()))).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `checks safety before analysis and rejects invalid images without invoking AI`() {
        provisionAllowed()
        val callsBeforeTest = generator.calls
        val invalid = MockMultipartFile("file", "capture.png", "image/png", byteArrayOf(1, 2, 3))
        mvc.perform(multipart("/transaction-captures/analyze").file(invalid).param("cardIssuer", "SHINHAN").param("year", "2026").with(csrf()).with(oidcLogin().oidcUser(TestOidcUsers.allowed()))).andExpect(status().isBadRequest)
        assertEquals(callsBeforeTest, generator.calls)
        val valid = MockMultipartFile("file", "capture.png", "image/png", CaptureImageSafetyGateTests.imageBytes())
        val result = mvc.perform(multipart("/transaction-captures/analyze").file(valid).param("cardIssuer", "SHINHAN").param("year", "2026").with(csrf()).with(oidcLogin().oidcUser(TestOidcUsers.allowed()))).andExpect(status().isOk).andReturn()
        assertEquals(1, mapper.readTree(result.response.contentAsString).get("entries").size())
        assertEquals(callsBeforeTest + 1, generator.calls)
        val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(requireNotNull(generator.lastImage)))
        assertEquals(java.awt.Color.BLACK.rgb, image.getRGB(10, 10))
    }

    private fun post(action: String, body: String) = mvc.post("/transaction-captures/$action") {
        contentType = MediaType.APPLICATION_JSON; content = body; with(csrf()); with(oidcLogin().oidcUser(TestOidcUsers.allowed()))
    }

    private fun provisionAllowed(): CurrentUser =
        accounts.provision(TestOidcUsers.allowed()).also { user ->
            privacyConsentService.update(user.id, privacyPolicyAgreed = true, aiOverseasTransferAgreed = true)
        }

    private fun request(payer: Long): String = mapper.writeValueAsString(mapOf(
        "requestId" to UUID.randomUUID(), "payerId" to payer, "cardIssuer" to "SHINHAN",
        "candidates" to listOf(mapOf("merchant" to "테스트마트", "amount" to 12000, "occurredAt" to "2026-09-08T08:56:00+09:00", "category" to "LIVING")),
    ))
}

class StubCaptureGenerator : CaptureGenerator {
    var calls = 0
    var lastImage: ByteArray? = null

    override fun generate(
        image: ByteArray,
        userId: Long,
        cardIssuer: com.woorijip.api.transaction.CardIssuer,
        year: Int,
    ): GeneratedCapture {
        calls++
        lastImage = image
        return GeneratedCapture(
            listOf(
                GeneratedCaptureEntry(
                    "테스트마트",
                    12_000,
                    "2026-09-08T08:56:00+09:00",
                    TransactionCategory.LIVING,
                    CaptureEntryStatus.APPROVAL,
                ),
            ),
        )
    }
}

@TestConfiguration(proxyBeanMethods = false)
class CaptureTestConfiguration {
    @Bean
    @Primary
    fun captureGenerator() = StubCaptureGenerator()

    @Bean
    @Primary
    fun captureTextReader(): CaptureTextReader = CaptureTextReader {
        listOf(
            CaptureImageSafetyGateTests.word("테스트마트", 20, 200),
            CaptureImageSafetyGateTests.word("12,000원", 350, 200),
        )
    }
}
