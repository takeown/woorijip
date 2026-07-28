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
import org.hamcrest.Matchers.hasSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertNotNull

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
        "app.auth.bootstrap-household-name=통계 테스트 우리집",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class SpendingStatisticsControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `summarizes a month and compares it with the previous month`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val partnerId = createMember(currentUser.householdId, "배우자")
        saveTransaction(currentUser.householdId, currentUser.id, 10_000, "식비", "2026-06-15T12:00:00+09:00")
        saveTransaction(currentUser.householdId, currentUser.id, 12_000, "식비", "2026-07-01T00:00:00+09:00")
        saveTransaction(
            currentUser.householdId,
            partnerId,
            8_000,
            "식비",
            "2026-07-15T10:00:00+09:00",
            PaymentMethod.CASH,
        )
        saveTransaction(currentUser.householdId, currentUser.id, 30_000, "생활", "2026-08-01T00:00:00+09:00")

        val otherHouseholdId = createHousehold("다른 집")
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")
        saveTransaction(otherHouseholdId, otherUserId, 999_000, "식비", "2026-07-10T12:00:00+09:00")

        mockMvc
            .get("/statistics/spending") {
                param("period", "month")
                param("date", "2026-07-26")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.period") { value("MONTH") }
                jsonPath("$.referenceDate") { value("2026-07-26") }
                jsonPath("$.startDate") { value("2026-07-01") }
                jsonPath("$.endDateExclusive") { value("2026-08-01") }
                jsonPath("$.current.totalAmount") { value(20_000) }
                jsonPath("$.current.transactionCount") { value(2) }
                jsonPath("$.previous.totalAmount") { value(10_000) }
                jsonPath("$.amountChange") { value(10_000) }
                jsonPath("$.changeRatePercent") { value(100.0) }
                jsonPath("$.byPayer", hasSize<Any>(2))
                jsonPath("$.byPayer[0].label") { value("첫 번째 사용자") }
                jsonPath("$.byPayer[0].amount") { value(12_000) }
                jsonPath("$.byPayer[1].label") { value("배우자") }
                jsonPath("$.byPaymentMethod[0].label") { value("카드") }
                jsonPath("$.byPaymentMethod[1].label") { value("현금") }
                jsonPath("$.byCategory", hasSize<Any>(1))
                jsonPath("$.byCategory[0].label") { value("식비") }
                jsonPath("$.byCategory[0].amount") { value(20_000) }
            }
    }

    @Test
    fun `uses Seoul day and Monday based week boundaries`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        saveTransaction(currentUser.householdId, currentUser.id, 5_000, "식비", "2026-07-12T23:59:59+09:00")
        saveTransaction(currentUser.householdId, currentUser.id, 8_000, "식비", "2026-07-15T00:00:00+09:00")
        saveTransaction(currentUser.householdId, currentUser.id, 3_000, "교통", "2026-07-15T23:59:59+09:00")
        saveTransaction(currentUser.householdId, currentUser.id, 7_000, "식비", "2026-07-20T00:00:00+09:00")

        mockMvc
            .get("/statistics/spending") {
                param("period", "day")
                param("date", "2026-07-15")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.startDate") { value("2026-07-15") }
                jsonPath("$.endDateExclusive") { value("2026-07-16") }
                jsonPath("$.current.totalAmount") { value(11_000) }
                jsonPath("$.current.transactionCount") { value(2) }
            }

        mockMvc
            .get("/statistics/spending") {
                param("period", "week")
                param("date", "2026-07-16")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.startDate") { value("2026-07-13") }
                jsonPath("$.endDateExclusive") { value("2026-07-20") }
                jsonPath("$.current.totalAmount") { value(11_000) }
                jsonPath("$.current.transactionCount") { value(2) }
            }
    }

    @Test
    fun `filters current and previous statistics by the selected household payer`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val partnerId = createMember(currentUser.householdId, "배우자")
        saveTransaction(currentUser.householdId, currentUser.id, 10_000, "식비", "2026-06-15T12:00:00+09:00")
        saveTransaction(currentUser.householdId, currentUser.id, 12_000, "식비", "2026-07-10T12:00:00+09:00")
        saveTransaction(
            currentUser.householdId,
            partnerId,
            8_000,
            "생활",
            "2026-07-15T12:00:00+09:00",
            PaymentMethod.CASH,
        )

        mockMvc
            .get("/statistics/spending") {
                param("period", "month")
                param("payer", "me")
                param("date", "2026-07-26")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.payer") { value("ME") }
                jsonPath("$.current.totalAmount") { value(12_000) }
                jsonPath("$.previous.totalAmount") { value(10_000) }
                jsonPath("$.amountChange") { value(2_000) }
                jsonPath("$.changeRatePercent") { value(20.0) }
                jsonPath("$.byPayer", hasSize<Any>(1))
                jsonPath("$.byPayer[0].label") { value("첫 번째 사용자") }
                jsonPath("$.byPaymentMethod", hasSize<Any>(1))
                jsonPath("$.byPaymentMethod[0].label") { value("카드") }
                jsonPath("$.byCategory[0].label") { value("식비") }
            }

        mockMvc
            .get("/statistics/spending") {
                param("period", "month")
                param("payer", "partner")
                param("date", "2026-07-26")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.payer") { value("PARTNER") }
                jsonPath("$.current.totalAmount") { value(8_000) }
                jsonPath("$.previous.totalAmount") { value(0) }
                jsonPath("$.byPayer", hasSize<Any>(1))
                jsonPath("$.byPayer[0].label") { value("배우자") }
                jsonPath("$.byPaymentMethod[0].label") { value("현금") }
                jsonPath("$.byCategory[0].label") { value("생활") }
            }
    }

    @Test
    fun `returns an empty summary and rejects invalid or anonymous requests`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .get("/statistics/spending") {
                param("period", "month")
                param("date", "2026-10-01")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.current.totalAmount") { value(0) }
                jsonPath("$.current.transactionCount") { value(0) }
                jsonPath("$.previous.totalAmount") { value(0) }
                jsonPath("$.changeRatePercent") { doesNotExist() }
                jsonPath("$.byPayer", hasSize<Any>(0))
            }

        mockMvc
            .get("/statistics/spending") {
                param("period", "year")
                with(allowedOidcLogin())
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .get("/statistics/spending") {
                param("payer", "other")
                with(allowedOidcLogin())
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .get("/statistics/spending")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    private fun saveTransaction(
        householdId: Long,
        payerId: Long,
        amount: Long,
        category: String,
        occurredAt: String,
        paymentMethod: PaymentMethod = PaymentMethod.CARD,
    ) {
        transactionRepository.save(
            Transaction(
                householdId = householdId,
                payerId = payerId,
                merchant = "테스트 가맹점",
                description = null,
                amount = amount,
                category = TransactionCategory.entries.single { it.label == category },
                paymentMethod = paymentMethod,
                cardIssuer = if (paymentMethod == PaymentMethod.CARD) CardIssuer.SHINHAN else null,
                occurredAt = OffsetDateTime.parse(occurredAt),
                createdAt = now,
            ),
        )
    }

    private fun createHousehold(name: String): Long =
        assertNotNull(
            householdRepository.save(
                Household(name = name, createdAt = now),
            ).id,
        )

    private fun createMember(
        householdId: Long,
        displayName: String,
    ): Long {
        val userId = assertNotNull(
            appUserRepository.save(
                AppUser(displayName = displayName, createdAt = now),
            ).id,
        )
        householdMembershipRepository.save(
            HouseholdMembership(
                householdId = householdId,
                userId = userId,
                createdAt = now,
            ),
        )
        return userId
    }

    private fun allowedOidcLogin() = oidcLogin().oidcUser(TestOidcUsers.allowed())

    private companion object {
        val now: OffsetDateTime = OffsetDateTime.parse("2026-07-26T09:00:00+09:00")
    }
}
