package com.woorijip.api.transaction

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.auth.TestOidcUsers
import com.woorijip.api.household.Household
import com.woorijip.api.household.HouseholdMembership
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.household.HouseholdRepository
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertNotNull

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
        "app.auth.bootstrap-household-name=테스트 우리집",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class TransactionControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `creates household transactions and filters them by payer`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val partnerId = createMember(currentUser.householdId, "배우자")

        createTransaction(currentUser.id, "김밥천국", "점심 식사")
        createTransaction(partnerId, "동네마트")

        mockMvc
            .get("/transactions") {
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(2))
            }

        mockMvc
            .get("/transactions") {
                param("payer", "me")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(1))
                jsonPath("$[0].merchant") { value("김밥천국") }
                jsonPath("$[0].description") { value("점심 식사") }
                jsonPath("$[0].payerId") { value(currentUser.id) }
                jsonPath("$[0].category") { value("FOOD") }
                jsonPath("$[0].tags", hasSize<Any>(2))
                jsonPath("$[0].classificationSource") { value("USER") }
                jsonPath("$[0].classificationConfidence") { value("HIGH") }
                jsonPath("$[0].classificationConfirmedAt") { exists() }
            }

        mockMvc
            .get("/transactions") {
                param("payer", "partner")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(1))
                jsonPath("$[0].merchant") { value("동네마트") }
                jsonPath("$[0].payerId") { value(partnerId) }
            }
    }

    @Test
    fun `lists only members of the current household`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        createMember(currentUser.householdId, "배우자")
        createMember(createHousehold("다른 집"), "다른 사용자")

        mockMvc
            .get("/households/current/members") {
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(2))
                jsonPath("$[0].displayName") { value("첫 번째 사용자") }
                jsonPath("$[1].displayName") { value("배우자") }
            }
    }

    @Test
    fun `prevents creating or reading transactions across households`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        createTransaction(currentUser.id, "우리 집 거래")
        val otherHouseholdId = createHousehold("다른 집")
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")
        transactionRepository.save(
            Transaction(
                householdId = otherHouseholdId,
                payerId = otherUserId,
                merchant = "다른 집 거래",
                description = null,
                amount = 10_000,
                category = TransactionCategory.FOOD,
                paymentMethod = PaymentMethod.UNKNOWN,
                cardIssuer = null,
                occurredAt = now,
                createdAt = now,
            ),
        )

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(otherUserId, "허용되지 않는 거래")
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .get("/transactions") {
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(1))
                jsonPath("$[0].merchant") { value("우리 집 거래") }
            }
    }

    @Test
    fun `rejects invalid transaction fields and payer filters`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "payerId": 0,
                      "merchant": " ",
                      "amount": 0,
                      "category": "",
                      "paymentMethod": null,
                      "occurredAt": null
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .get("/transactions") {
                param("payer", "unknown")
                with(allowedOidcLogin())
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `validates payment method and card issuer combinations`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "카드 결제", "CARD", null)
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "현금 결제", "CASH", "SHINHAN")
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "과거 결제", "UNKNOWN", null)
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `creates a cash transaction without a card issuer`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "현금 결제", "CASH", null)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.paymentMethod") { value("CASH") }
                jsonPath("$.cardIssuer") { doesNotExist() }
            }
    }

    @Test
    fun `stores an optional description and normalizes a blank description`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "동네마트", description = "  세제와 휴지  ")
            }.andExpect {
                status { isCreated() }
                jsonPath("$.description") { value("세제와 휴지") }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "김밥천국", description = " ")
            }.andExpect {
                status { isCreated() }
                jsonPath("$.description") { doesNotExist() }
            }
    }

    @Test
    fun `updates a household transaction and rejects a stale update`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        createTransaction(currentUser.id, "수정 전")
        val transaction = transactionRepository.findAll().single()
        val transactionId = assertNotNull(transaction.id)

        val updateJson = """
            {
              "expectedUpdatedAt": "${transaction.updatedAt}",
              "payerId": ${currentUser.id},
              "merchant": "수정 후",
              "description": "수정한 내역",
              "amount": 12000,
              "category": "LIVING",
              "tags": ["UTILITY"],
              "paymentMethod": "CASH",
              "cardIssuer": null,
              "occurredAt": "2026-07-16T10:00:00+09:00"
            }
        """.trimIndent()

        mockMvc
            .put("/transactions/$transactionId") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = updateJson
            }.andExpect {
                status { isOk() }
                jsonPath("$.merchant") { value("수정 후") }
                jsonPath("$.category") { value("LIVING") }
                jsonPath("$.tags[0]") { value("UTILITY") }
                jsonPath("$.classificationSource") { value("USER") }
                jsonPath("$.classificationConfidence") { value("HIGH") }
            }

        mockMvc
            .put("/transactions/$transactionId") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = updateJson
            }.andExpect {
                status { isConflict() }
            }
    }

    @Test
    fun `deletes only a current household transaction`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        createTransaction(currentUser.id, "삭제할 거래")
        val transaction = transactionRepository.findAll().single()
        val transactionId = assertNotNull(transaction.id)

        mockMvc
            .delete("/transactions/$transactionId") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"expectedUpdatedAt":"${transaction.updatedAt}"}"""
            }.andExpect {
                status { isNoContent() }
            }

        mockMvc
            .delete("/transactions/$transactionId") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"expectedUpdatedAt":"${transaction.updatedAt}"}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `rejects a description longer than 500 characters`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "동네마트", description = "가".repeat(501))
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `requires authentication to access transactions`() {
        mockMvc
            .get("/transactions")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `allows browser preflight for editing and deleting transactions`() {
        listOf("PUT", "DELETE").forEach { method ->
            mockMvc
                .options("/transactions/1") {
                    header("Origin", "http://localhost:3100")
                    header("Access-Control-Request-Method", method)
                }.andExpect {
                    status { isOk() }
                    header { string("Access-Control-Allow-Methods", containsString(method)) }
                }
        }
    }

    private fun createTransaction(
        payerId: Long,
        merchant: String,
        description: String? = null,
    ) {
        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(payerId, merchant, description = description)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.payerId") { value(payerId) }
                jsonPath("$.merchant") { value(merchant) }
                if (description != null) {
                    jsonPath("$.description") { value(description) }
                }
                jsonPath("$.paymentMethod") { value("CARD") }
                jsonPath("$.cardIssuer") { value("SHINHAN") }
            }
    }

    private fun transactionJson(
        payerId: Long,
        merchant: String,
        paymentMethod: String = "CARD",
        cardIssuer: String? = "SHINHAN",
        description: String? = null,
    ) =
        """
        {
          "payerId": $payerId,
          "merchant": "$merchant",
          "description": ${description?.let { "\"$it\"" } ?: "null"},
          "amount": 8000,
          "category": "FOOD",
          "tags": ["SUBSCRIPTION", "RECURRING_PAYMENT"],
          "paymentMethod": "$paymentMethod",
          "cardIssuer": ${cardIssuer?.let { "\"$it\"" } ?: "null"},
          "occurredAt": "2026-07-15T12:30:00+09:00"
        }
        """.trimIndent()

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
        val now: OffsetDateTime = OffsetDateTime.parse("2026-07-15T12:30:00+09:00")
    }
}
