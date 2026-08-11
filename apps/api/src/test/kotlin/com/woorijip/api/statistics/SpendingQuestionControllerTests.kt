package com.woorijip.api.statistics

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.ai.GeneratedSpendingQuestion
import com.woorijip.api.ai.GeneratedSpendingQuestionStatus
import com.woorijip.api.ai.SpendingQuestionIntent
import com.woorijip.api.ai.SpendingQuestionInterpreter
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
import org.hamcrest.Matchers.hasSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
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
        "app.auth.bootstrap-household-name=가계 질문 테스트 우리집",
        "app.openai.spending-question-daily-limit=3",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, SpendingQuestionTestConfiguration::class)
@Transactional
class SpendingQuestionControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `answers a category question with comparison and household evidence`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, 10_000, TransactionCategory.FOOD, "2026-07-10T12:00:00+09:00")
        saveTransaction(
            currentUser.householdId,
            currentUser.id,
            15_000,
            TransactionCategory.FOOD,
            "2026-08-10T12:00:00+09:00",
            merchant = "우리집 식료품점",
        )
        saveTransaction(currentUser.householdId, currentUser.id, 40_000, TransactionCategory.LIVING, "2026-08-11T12:00:00+09:00")
        val otherHouseholdId = createHousehold("다른 집")
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")
        saveTransaction(
            otherHouseholdId,
            otherUserId,
            999_000,
            TransactionCategory.FOOD,
            "2026-08-10T12:00:00+09:00",
            merchant = "다른 집 식료품점",
        )

        mockMvc
            .post("/statistics/spending/questions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"question":"이번 달 식비 얼마 썼어?"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ANSWERED") }
                jsonPath("$.intent") { value("CATEGORY") }
                jsonPath("$.period") { value("MONTH") }
                jsonPath("$.category") { value("FOOD") }
                jsonPath("$.categoryLabel") { value("식비") }
                jsonPath("$.currentAmount") { value(15_000) }
                jsonPath("$.previousAmount") { value(10_000) }
                jsonPath("$.amountChange") { value(5_000) }
                jsonPath("$.message") { value("2026년 8월 식비는 15,000원입니다. 이전 같은 기간보다 5,000원 늘었습니다.") }
                jsonPath("$.evidenceTransactions", hasSize<Any>(1))
                jsonPath("$.evidenceTransactions[0].merchant") { value("우리집 식료품점") }
                jsonPath("$.remainingRequestsToday") { value(2) }
            }
    }

    @Test
    fun `answers the largest transaction question and returns at most three evidence rows`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, 5_000, TransactionCategory.FOOD, "2026-08-01T12:00:00+09:00", "첫 거래")
        saveTransaction(currentUser.householdId, currentUser.id, 30_000, TransactionCategory.LIVING, "2026-08-02T12:00:00+09:00", "가장 큰 거래")
        saveTransaction(currentUser.householdId, currentUser.id, 20_000, TransactionCategory.TRANSPORT, "2026-08-03T12:00:00+09:00", "세 번째 거래")
        saveTransaction(currentUser.householdId, currentUser.id, 10_000, TransactionCategory.HEALTH, "2026-08-04T12:00:00+09:00", "네 번째 거래")

        mockMvc
            .post("/statistics/spending/questions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"question":"이번 달 가장 큰 지출은 뭐야?"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.intent") { value("LARGEST_TRANSACTION") }
                jsonPath("$.currentAmount") { value(30_000) }
                jsonPath("$.message") { value("2026년 8월 가장 큰 지출은 가장 큰 거래 30,000원입니다.") }
                jsonPath("$.evidenceTransactions", hasSize<Any>(3))
                jsonPath("$.evidenceTransactions[0].merchant") { value("가장 큰 거래") }
                jsonPath("$.evidenceTransactions[1].merchant") { value("세 번째 거래") }
                jsonPath("$.evidenceTransactions[2].merchant") { value("네 번째 거래") }
            }
    }

    @Test
    fun `returns unsupported without querying household data`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/statistics/spending/questions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"question":"내년 예산 추천해줘"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UNSUPPORTED") }
                jsonPath("$.evidenceTransactions", hasSize<Any>(0))
                jsonPath("$.remainingRequestsToday") { value(2) }
            }
    }

    @Test
    fun `blocks sensitive input before consuming quota`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/statistics/spending/questions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"question":"first@example.com의 이번 달 식비 알려줘"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("SENSITIVE_AI_INPUT") }
            }

        askUnsupported().andExpect {
            status { isOk() }
            jsonPath("$.remainingRequestsToday") { value(2) }
        }
    }

    @Test
    fun `rejects a fourth daily question`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        repeat(3) { askUnsupported().andExpect { status { isOk() } } }
        askUnsupported().andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("AI_USAGE_LIMIT_EXCEEDED") }
        }
    }

    @Test
    fun `requires authentication csrf and a valid question`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/statistics/spending/questions") {
                with(csrf())
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"question":"이번 달 식비"}"""
            }.andExpect { status { isUnauthorized() } }

        mockMvc
            .post("/statistics/spending/questions") {
                with(allowedOidcLogin())
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"question":"이번 달 식비"}"""
            }.andExpect { status { isForbidden() } }

        mockMvc
            .post("/statistics/spending/questions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"question":" "}"""
            }.andExpect { status { isBadRequest() } }
    }

    private fun askUnsupported() =
        mockMvc.post("/statistics/spending/questions") {
            with(allowedOidcLogin())
            with(csrf())
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"question":"내년 예산 추천해줘"}"""
        }

    private fun saveTransaction(
        householdId: Long,
        payerId: Long,
        amount: Long,
        category: TransactionCategory,
        occurredAt: String,
        merchant: String = "테스트 가맹점",
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
                occurredAt = OffsetDateTime.parse(occurredAt),
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
        val now: OffsetDateTime = OffsetDateTime.parse("2026-08-11T12:00:00+09:00")
    }
}

@TestConfiguration(proxyBeanMethods = false)
class SpendingQuestionTestConfiguration {
    @Bean
    @Primary
    fun spendingQuestionInterpreter(): SpendingQuestionInterpreter =
        SpendingQuestionInterpreter { question, _ ->
            when {
                question.contains("예산") -> GeneratedSpendingQuestion(GeneratedSpendingQuestionStatus.UNSUPPORTED)
                question.contains("가장 큰") -> GeneratedSpendingQuestion(
                    status = GeneratedSpendingQuestionStatus.ANSWERABLE,
                    intent = SpendingQuestionIntent.LARGEST_TRANSACTION,
                    period = SpendingPeriod.MONTH,
                    referenceDate = "2026-08-11",
                    payer = SpendingPayer.ALL,
                )
                else -> GeneratedSpendingQuestion(
                    status = GeneratedSpendingQuestionStatus.ANSWERABLE,
                    intent = SpendingQuestionIntent.CATEGORY,
                    period = SpendingPeriod.MONTH,
                    referenceDate = "2026-08-11",
                    payer = SpendingPayer.ALL,
                    category = TransactionCategory.FOOD,
                )
            }
        }
}
