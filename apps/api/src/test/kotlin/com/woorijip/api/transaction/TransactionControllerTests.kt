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
import org.hamcrest.Matchers.hasSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
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

        createTransaction(currentUser.id, "김밥천국")
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
                jsonPath("$[0].payerId") { value(currentUser.id) }
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
                amount = 10_000,
                category = "식비",
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
    fun `requires authentication to access transactions`() {
        mockMvc
            .get("/transactions")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    private fun createTransaction(
        payerId: Long,
        merchant: String,
    ) {
        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(payerId, merchant)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.payerId") { value(payerId) }
                jsonPath("$.merchant") { value(merchant) }
            }
    }

    private fun transactionJson(
        payerId: Long,
        merchant: String,
    ) =
        """
        {
          "payerId": $payerId,
          "merchant": "$merchant",
          "amount": 8000,
          "category": "식비",
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
