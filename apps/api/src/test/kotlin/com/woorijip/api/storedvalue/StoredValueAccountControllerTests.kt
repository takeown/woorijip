package com.woorijip.api.storedvalue

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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertNotNull

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
        "app.auth.bootstrap-household-name=잔액 테스트 우리집",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class StoredValueAccountControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val householdRepository: HouseholdRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
    @Autowired private val repository: StoredValueAccountRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Test
    fun `creates only the stored value account selected for a household member`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val partnerId = createMember(currentUser.householdId, "배우자")

        mockMvc
            .get("/stored-value-accounts") {
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(0))
            }

        mockMvc
            .post("/stored-value-accounts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "ownerUserId": $partnerId,
                      "name": "임산부 바우처",
                      "category": "VOUCHER",
                      "automationKey": "PREGNANCY_VOUCHER"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.ownerUserId") { value(partnerId) }
                jsonPath("$.ownerDisplayName") { value("배우자") }
                jsonPath("$.name") { value("임산부 바우처") }
                jsonPath("$.category") { value("VOUCHER") }
                jsonPath("$.automationKey") { value("PREGNANCY_VOUCHER") }
                jsonPath("$.archived") { value(false) }
                jsonPath("$.canDelete") { value(true) }
            }

        mockMvc
            .get("/stored-value-accounts") {
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(1))
                jsonPath("$[0].ownerUserId") { value(partnerId) }
            }
    }

    @Test
    fun `creates and validates a directly named account category`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/stored-value-accounts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "ownerUserId": ${currentUser.id},
                      "name": "첫만남이용권",
                      "category": "OTHER",
                      "customCategoryName": "육아 지원금",
                      "automationKey": null
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.category") { value("OTHER") }
                jsonPath("$.customCategoryName") { value("육아 지원금") }
            }

        mockMvc
            .post("/stored-value-accounts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "ownerUserId": ${currentUser.id},
                      "name": "종류명 없는 계정",
                      "category": "OTHER",
                      "automationKey": null
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_STORED_VALUE_ACCOUNT") }
            }
    }

    @Test
    fun `increases and decreases a balance with manual adjustments`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val accountId = createAccount(currentUser.id, "온누리상품권", "GIFT_CERTIFICATE")

        mockMvc
            .post("/stored-value-accounts/$accountId/credits") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"balanceAmount":10000,"paidAmount":9300,"occurredAt":"2026-08-05T12:00:00+09:00"}
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.balance") { value(10_000) }
            }

        mockMvc
            .post("/stored-value-accounts/$accountId/adjustments") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "direction": "DECREASE",
                      "amount": 2400,
                      "reason": "누락 사용",
                      "occurredAt": "2026-08-05T13:00:00+09:00"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.balance") { value(7_600) }
                jsonPath("$.canDelete") { value(false) }
            }

        mockMvc
            .post("/stored-value-accounts/$accountId/adjustments") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "direction": "INCREASE",
                      "amount": 1000,
                      "reason": "환불",
                      "occurredAt": "2026-08-05T14:00:00+09:00"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.balance") { value(8_600) }
            }

        mockMvc
            .post("/stored-value-accounts/$accountId/adjustments") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "direction": "DECREASE",
                      "amount": 9000,
                      "reason": "잘못된 차감",
                      "occurredAt": "2026-08-05T15:00:00+09:00"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INSUFFICIENT_STORED_VALUE_BALANCE") }
            }
    }

    @Test
    fun `updates archives restores and deletes an unused custom account`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val accountId = createAccount(currentUser.id, "서울사랑상품권", "LOCAL_CURRENCY")

        mockMvc
            .patch("/stored-value-accounts/$accountId") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"name":"서울페이","category":"LOCAL_CURRENCY","archived":true}
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("서울페이") }
                jsonPath("$.archived") { value(true) }
            }

        mockMvc
            .patch("/stored-value-accounts/$accountId") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"name":"서울페이","category":"LOCAL_CURRENCY","archived":false}
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.archived") { value(false) }
            }

        mockMvc
            .delete("/stored-value-accounts/$accountId") {
                with(allowedOidcLogin())
                with(csrf())
            }.andExpect {
                status { isNoContent() }
            }

        mockMvc
            .get("/stored-value-accounts") {
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(0))
            }
    }

    @Test
    fun `keeps used accounts as history and blocks other household access`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val accountId = createAccount(currentUser.id, "온누리상품권", "GIFT_CERTIFICATE", "ONNURI_GIFT_CERTIFICATE")

        mockMvc
            .post("/stored-value-accounts/$accountId/credits") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"balanceAmount":10000,"paidAmount":9300,"occurredAt":"2026-08-04T12:00:00+09:00"}
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.canDelete") { value(false) }
            }

        mockMvc
            .delete("/stored-value-accounts/$accountId") {
                with(allowedOidcLogin())
                with(csrf())
            }.andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("STORED_VALUE_ACCOUNT_IN_USE") }
            }

        mockMvc
            .patch("/stored-value-accounts/$accountId") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"name":"온누리상품권","category":"GIFT_CERTIFICATE","archived":true}
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.archived") { value(true) }
            }

        mockMvc
            .post("/stored-value-accounts/$accountId/credits") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"balanceAmount":1000,"paidAmount":930,"occurredAt":"2026-08-04T13:00:00+09:00"}
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_STORED_VALUE_ACCOUNT") }
            }

        val otherHouseholdId = assertNotNull(
            householdRepository.save(Household(name = "다른 집", createdAt = now)).id,
        )
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")
        val otherAccountId = repository.create(
            householdId = otherHouseholdId,
            ownerUserId = otherUserId,
            name = "다른 집 상품권",
            category = StoredValueAccountCategory.GIFT_CERTIFICATE,
            automationKey = null,
            createdAt = now,
        )

        mockMvc
            .delete("/stored-value-accounts/$otherAccountId") {
                with(allowedOidcLogin())
                with(csrf())
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("STORED_VALUE_ACCOUNT_NOT_FOUND") }
            }
    }

    @Test
    fun `allows browser preflight for stored value account management`() {
        listOf("PATCH", "DELETE").forEach { method ->
            mockMvc
                .options("/stored-value-accounts/1") {
                    header("Origin", "http://localhost:3100")
                    header("Access-Control-Request-Method", method)
                }.andExpect {
                    status { isOk() }
                    header { string("Access-Control-Allow-Methods", containsString(method)) }
                }
        }
    }

    private fun createAccount(
        ownerUserId: Long,
        name: String,
        category: String,
        automationKey: String? = null,
    ): Long {
        val response = mockMvc
            .post("/stored-value-accounts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "ownerUserId": $ownerUserId,
                      "name": "$name",
                      "category": "$category",
                      "automationKey": ${automationKey?.let { "\"$it\"" } ?: "null"}
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
            }.andReturn()
        return objectMapper.readTree(response.response.contentAsString).path("id").longValue()
    }

    private fun createMember(householdId: Long, displayName: String): Long {
        val userId = assertNotNull(appUserRepository.save(AppUser(displayName = displayName, createdAt = now)).id)
        householdMembershipRepository.save(
            HouseholdMembership(householdId = householdId, userId = userId, createdAt = now),
        )
        return userId
    }

    private fun allowedOidcLogin() = oidcLogin().oidcUser(TestOidcUsers.allowed())

    private companion object {
        val now: OffsetDateTime = OffsetDateTime.parse("2026-08-04T12:00:00+09:00")
    }
}
