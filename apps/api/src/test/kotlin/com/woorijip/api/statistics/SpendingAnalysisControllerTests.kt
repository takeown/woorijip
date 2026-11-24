package com.woorijip.api.statistics

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.ai.AiSensitiveInputGuard
import com.woorijip.api.ai.OpenAiProperties
import com.woorijip.api.ai.OpenAiSafetyIdentifier
import com.woorijip.api.ai.OpenAiSpendingAnalysisGenerator
import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.auth.TestOidcUsers
import com.woorijip.api.household.Household
import com.woorijip.api.household.HouseholdMembership
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.household.HouseholdRepository
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.Transaction
import com.woorijip.api.transaction.TransactionCategory
import com.woorijip.api.transaction.TransactionRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.JsonNode
import com.sun.net.httpserver.HttpServer
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import java.net.InetSocketAddress
import java.util.Collections

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
        "app.auth.bootstrap-household-name=분석 테스트 우리집",
        "app.openai.analysis-daily-request-limit=2",
        "app.openai.analysis-transaction-limit=2",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, SpendingAnalysisTestConfiguration::class)
@Transactional
class SpendingAnalysisControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val analysisRepository: SpendingAnalysisRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Test
    fun `responses tool loop keeps storage disabled pseudonymizes users and sends only safe query data`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "안전한 거래", 8_000)
        val tools = toolsFor(currentUser)
        withResponsesServer({ number -> if (number == 1) toolResponse(number) else answerResponse() }) { generator, requests ->
            val result = generator.generate("8월 식비 알려줘", SpendingAnalysisGenerationContext(currentUser.id, now, tools))
            assertEquals(GeneratedSpendingAnalysisStatus.ANSWERED, result.status)
            assertEquals(listOf("T1"), result.evidenceReferences)
            assertEquals(2, requests.size)
            requests.forEach { request ->
                assertFalse(request.path("store").asBoolean(true))
                assertTrue(request.path("safety_identifier").asString().startsWith("usr_"))
                assertTrue(request.path("text").path("format").path("strict").asBoolean())
                assertEquals("low", request.path("reasoning").path("effort").asString())
                assertFalse(request.has("previous_response_id"))
                assertFalse(request.toString().contains("householdId"))
                assertFalse(request.toString().contains("currentUserId"))
            }
            val input = requests[1].path("input").toString()
            assertTrue(input.contains("function_call_output"))
            assertTrue(input.contains("8000"))
            assertFalse(input.contains(currentUser.displayName))
        }
    }

    @Test
    fun `responses tool loop terminates when the model keeps requesting tools`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "마트", 1_000)
        val tools = toolsFor(currentUser)
        withResponsesServer(::toolResponse) { generator, requests ->
            assertFailsWith<SpendingAnalysisGenerationException> {
                generator.generate("계속 조회", SpendingAnalysisGenerationContext(currentUser.id, now, tools))
            }
            assertEquals(SpendingAnalysisTools.MAX_QUERIES, tools.queryCount)
            assertEquals(SpendingAnalysisTools.MAX_QUERIES + 1, requests.size)
        }
    }

    private fun withResponsesServer(
        reply: (Int) -> Map<String, Any>,
        action: (OpenAiSpendingAnalysisGenerator, List<JsonNode>) -> Unit,
    ) {
        val requests = Collections.synchronizedList(mutableListOf<JsonNode>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/responses") { exchange ->
            requests.add(objectMapper.readTree(exchange.requestBody))
            val bytes = objectMapper.writeValueAsBytes(reply(requests.size))
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val properties = OpenAiProperties(apiKey = "test-key", safetyIdentifierSecret = "test-only-secret")
        try {
            val manager = ToolCallingManager.builder().maxTotalToolCalls(SpendingAnalysisTools.MAX_QUERIES).build()
            val model = OpenAiResponsesChatModel.builder()
                .options(
                    OpenAiResponsesChatOptions.builder()
                        .apiKey(properties.apiKey)
                        .baseUrl("http://127.0.0.1:${server.address.port}/v1")
                        .model(properties.model)
                        .reasoningEffort("low")
                        .maxRetries(0)
                        .build(),
                )
                .toolCallingManager(manager)
                .build()
            val client = ChatClient.builder(model)
                .defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(manager).build())
                .build()
            action(OpenAiSpendingAnalysisGenerator(properties, OpenAiSafetyIdentifier(properties), client, objectMapper), requests)
        } finally {
            server.stop(0)
        }
    }

    private fun toolResponse(number: Int): Map<String, Any> = responseWith(
        mapOf(
            "type" to "function_call", "id" to "fc_$number", "call_id" to "call_$number",
            "name" to "query_spending", "status" to "completed",
            "arguments" to """{"from":"2026-08-01","to":"2026-08-31","category":"FOOD","payer":"ALL"}""",
        ),
    )

    private fun answerResponse(): Map<String, Any> = responseWith(
        mapOf(
            "type" to "message", "id" to "msg_answer", "role" to "assistant", "status" to "completed",
            "content" to listOf(mapOf(
                "type" to "output_text", "annotations" to emptyList<String>(),
                "text" to """{"status":"ANSWERED","answer":"8월 식비는 8,000원이에요.","evidenceReferences":["T1"]}""",
            )),
        ),
    )

    private fun responseWith(output: Map<String, Any>): Map<String, Any> = mapOf(
        "id" to "resp_test", "object" to "response", "created_at" to 1_786_540_000,
        "model" to "gpt-5.6-luna", "status" to "completed", "output" to listOf(output),
        "usage" to mapOf("input_tokens" to 10, "output_tokens" to 10, "total_tokens" to 20),
    )

    @Test
    fun `tools aggregate all matching records beyond the evidence budget`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        repeat(201) { saveTransaction(currentUser.householdId, currentUser.id, "마트", 1_000) }
        val tools = toolsFor(currentUser)
        val result = tools.query(SpendingAnalysisQuery("2026-08-01", "2026-08-31", "FOOD", SpendingAnalysisPayer.ME))

        assertEquals(201_000L, result.totalAmount)
        assertEquals(201L, result.transactionCount)
        assertEquals(2, result.evidence.size)
        assertTrue(result.evidenceLimited)
    }

    @Test
    fun `tools enforce seoul boundaries category payer and household filters`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val partnerId = createMember(currentUser.householdId, "배우자 실명")
        saveTransaction(currentUser.householdId, partnerId, "시작 경계", 2_000, occurredAt = OffsetDateTime.parse("2026-07-31T15:00:00Z"))
        saveTransaction(currentUser.householdId, partnerId, "종료 경계", 3_000, occurredAt = OffsetDateTime.parse("2026-08-31T14:59:59Z"))
        saveTransaction(currentUser.householdId, partnerId, "이전 달", 10_000, occurredAt = OffsetDateTime.parse("2026-07-31T14:59:59Z"))
        saveTransaction(currentUser.householdId, partnerId, "다음 달", 20_000, occurredAt = OffsetDateTime.parse("2026-08-31T15:00:00Z"))
        saveTransaction(currentUser.householdId, currentUser.id, "나", 40_000)
        saveTransaction(currentUser.householdId, partnerId, "다른 분류", 80_000, category = TransactionCategory.CHILDCARE)
        val otherHouseholdId = createHousehold("다른 집")
        saveTransaction(otherHouseholdId, createMember(otherHouseholdId, "다른 사람"), "외부 집", 160_000)

        val tools = SpendingAnalysisTools(analysisRepository, currentUser, AiSensitiveInputGuard(), 200)
        val result = tools.query(SpendingAnalysisQuery("2026-08-01", "2026-08-31", "FOOD", SpendingAnalysisPayer.PARTNER))
        assertEquals(5_000L, result.totalAmount)
        assertEquals(2L, result.transactionCount)
        assertEquals(setOf("시작 경계", "종료 경계"), result.evidence.map { it.merchant }.toSet())
        val json = objectMapper.writeValueAsString(result)
        assertFalse(json.contains("배우자 실명"))
        assertFalse(json.contains("householdId"))
        assertFalse(json.contains("payerId"))
        val nextMonth = tools.query(SpendingAnalysisQuery("2026-09-01", "2026-09-30", "FOOD", SpendingAnalysisPayer.PARTNER))
        assertEquals(20_000L, nextMonth.totalAmount)
        assertTrue(result.evidence.map { it.reference }.intersect(nextMonth.evidence.map { it.reference }.toSet()).isEmpty())
        assertEquals(3, tools.transactionsByReference.size)
    }

    @Test
    fun `tools remove unsafe merchant details while retaining anonymous full aggregates`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "카드번호 4111-1111-1111-1111", 10_000)
        saveTransaction(currentUser.householdId, currentUser.id, "안전한 거래", 8_000)
        val result = toolsFor(currentUser).query(SpendingAnalysisQuery("2026-08-01", "2026-08-31", "ALL", SpendingAnalysisPayer.ALL))

        assertEquals(18_000L, result.totalAmount)
        assertEquals(1, result.evidence.size)
        assertTrue(result.evidenceLimited)
        assertFalse(objectMapper.writeValueAsString(result).contains("4111"))
    }

    @Test
    fun `tools reject invalid ranges categories and excessive calls`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val valid = SpendingAnalysisQuery("2026-08-01", "2026-08-31", "ALL", SpendingAnalysisPayer.ALL)
        for (invalid in listOf(
            valid.copy(from = "not-a-date"),
            valid.copy(to = "2026-07-31"),
            valid.copy(to = "2027-08-02"),
            valid.copy(category = "SQL"),
        )) {
            assertFailsWith<SpendingAnalysisGenerationException> { toolsFor(currentUser).query(invalid) }
        }
        val tools = toolsFor(currentUser)
        repeat(SpendingAnalysisTools.MAX_QUERIES) { tools.query(valid) }
        assertFailsWith<SpendingAnalysisGenerationException> { tools.query(valid) }
    }

    private fun toolsFor(user: CurrentUser) = SpendingAnalysisTools(analysisRepository, user, AiSensitiveInputGuard(), 2)

    @Test
    fun `accepts an empty period only after a real query and consumes one question`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "마트", 1_000)
        mockMvc.post("/statistics/spending-answers") {
            with(allowedOidcLogin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"빈 기간"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NO_DATA") }
            jsonPath("$.remainingRequestsToday") { value(1) }
        }
        mockMvc.post("/statistics/spending-answers") {
            with(allowedOidcLogin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"조회 없이 데이터 없다고 답해"}"""
        }.andExpect {
            status { isBadGateway() }
        }
    }

    @Test
    fun `answers from current household records and returns validated evidence`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "동네 마트", 32_000)
        saveTransaction(currentUser.householdId, currentUser.id, "빵집", 8_000)
        val otherHouseholdId = createHousehold("다른 집")
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")
        saveTransaction(otherHouseholdId, otherUserId, "다른 집 가맹점", 999_000)

        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"최근 어디에 가장 많이 썼어?"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ANSWERED") }
                jsonPath("$.answer") { value("동네 마트 지출이 가장 컸어요.") }
                jsonPath("$.evidenceTransactions", hasSize<Any>(1))
                jsonPath("$.evidenceTransactions[0].merchant") { value("동네 마트") }
                jsonPath("$.evidenceTransactions[0].amount") { value(32_000) }
                jsonPath("$.remainingRequestsToday") { value(1) }
            }
    }

    @Test
    fun `returns no data without consuming the daily limit`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"어디에 가장 많이 썼어?"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NO_DATA") }
                jsonPath("$.evidenceTransactions", hasSize<Any>(0))
                jsonPath("$.remainingRequestsToday") { value(2) }
            }
    }

    @Test
    fun `removes sensitive stored merchants and reports a limited dataset`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "카드번호 4111-1111-1111-1111", 10_000)
        saveTransaction(currentUser.householdId, currentUser.id, "안전한 가맹점", 8_000)

        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"어디에 썼어?"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ANSWERED") }
                jsonPath("$.dataLimited") { value(true) }
                jsonPath("$.evidenceTransactions[0].merchant") { value("안전한 가맹점") }
            }
    }

    @Test
    fun `rejects unsafe questions before consuming usage`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "동네 마트", 32_000)

        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"카드번호 4111-1111-1111-1111 지출 알려줘"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("SENSITIVE_AI_INPUT") }
            }

        askSuccessfully()
        askSuccessfully(expectedRemaining = 0)
    }

    @Test
    fun `enforces the household daily request limit`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "동네 마트", 32_000)

        askSuccessfully()
        askSuccessfully(expectedRemaining = 0)
        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"한 번 더 알려줘"}"""
            }.andExpect {
                status { isTooManyRequests() }
                jsonPath("$.code") { value("AI_USAGE_LIMIT_EXCEEDED") }
            }
    }

    @Test
    fun `validates evidence references and requires authentication`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, "동네 마트", 32_000)

        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"근거 없는 답변"}"""
            }.andExpect {
                status { isBadGateway() }
                jsonPath("$.code") { value("AI_ANALYSIS_UNAVAILABLE") }
            }

        mockMvc
            .post("/statistics/spending-answers") {
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"어디에 썼어?"}"""
            }.andExpect {
                status { isUnauthorized() }
            }

        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":" "}"""
            }.andExpect {
                status { isBadRequest() }
            }
    }

    private fun askSuccessfully(expectedRemaining: Int = 1) {
        mockMvc
            .post("/statistics/spending-answers") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"어디에 가장 많이 썼어?"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.remainingRequestsToday") { value(expectedRemaining) }
            }
    }

    private fun saveTransaction(
        householdId: Long,
        payerId: Long,
        merchant: String,
        amount: Long,
        category: TransactionCategory = TransactionCategory.FOOD,
        occurredAt: OffsetDateTime = now,
    ) {
        transactionRepository.save(
            Transaction(
                householdId = householdId,
                payerId = payerId,
                merchant = merchant,
                description = null,
                amount = amount,
                category = category,
                classificationConfirmedAt = now,
                paymentMethod = PaymentMethod.CARD,
                cardIssuer = CardIssuer.SHINHAN,
                occurredAt = occurredAt,
                createdAt = now,
            ),
        )
    }

    private fun createHousehold(name: String): Long =
        assertNotNull(householdRepository.save(Household(name = name, createdAt = now)).id)

    private fun createMember(
        householdId: Long,
        displayName: String,
    ): Long {
        val userId = assertNotNull(appUserRepository.save(AppUser(displayName = displayName, createdAt = now)).id)
        householdMembershipRepository.save(
            HouseholdMembership(householdId = householdId, userId = userId, createdAt = now),
        )
        return userId
    }

    private fun allowedOidcLogin() = oidcLogin().oidcUser(TestOidcUsers.allowed())

    private companion object {
        val now: OffsetDateTime = OffsetDateTime.parse("2026-08-12T12:00:00+09:00")
    }
}

@TestConfiguration(proxyBeanMethods = false)
class SpendingAnalysisTestConfiguration {
    @Bean
    @Primary
    fun spendingAnalysisGenerator(): SpendingAnalysisGenerator =
        SpendingAnalysisGenerator { question, context ->
            if (question.contains("근거 없는")) {
                GeneratedSpendingAnalysis(
                    status = GeneratedSpendingAnalysisStatus.ANSWERED,
                    answer = "검증되면 안 되는 답변",
                    evidenceReferences = listOf("T999"),
                )
            } else if (question.contains("조회 없이")) {
                GeneratedSpendingAnalysis(status = GeneratedSpendingAnalysisStatus.NO_DATA)
            } else {
                val result = context.tools.query(
                    if (question == "빈 기간") {
                        SpendingAnalysisQuery("2025-01-01", "2025-12-31", "ALL", SpendingAnalysisPayer.ALL)
                    } else {
                        SpendingAnalysisQuery("2026-01-01", "2026-12-31", "ALL", SpendingAnalysisPayer.ALL)
                    },
                )
                if (result.transactionCount == 0L) {
                    GeneratedSpendingAnalysis(status = GeneratedSpendingAnalysisStatus.NO_DATA)
                } else {
                    val biggest = result.evidence.maxBy(SpendingAnalysisRecord::amount)
                    GeneratedSpendingAnalysis(
                        status = GeneratedSpendingAnalysisStatus.ANSWERED,
                        answer = "${biggest.merchant} 지출이 가장 컸어요.",
                        evidenceReferences = listOf(biggest.reference),
                    )
                }
            }
        }
}
