package com.woorijip.api.statistics

import com.woorijip.api.TestcontainersConfiguration
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
) {
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
    ) {
        transactionRepository.save(
            Transaction(
                householdId = householdId,
                payerId = payerId,
                merchant = merchant,
                description = null,
                amount = amount,
                category = TransactionCategory.FOOD,
                classificationConfirmedAt = now,
                paymentMethod = PaymentMethod.CARD,
                cardIssuer = CardIssuer.SHINHAN,
                occurredAt = now,
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
            } else {
                val biggest = context.records.maxBy(SpendingAnalysisRecord::amount)
                GeneratedSpendingAnalysis(
                    status = GeneratedSpendingAnalysisStatus.ANSWERED,
                    answer = "${biggest.merchant} 지출이 가장 컸어요.",
                    evidenceReferences = listOf(biggest.reference),
                )
            }
        }
}
