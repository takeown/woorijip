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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    @Autowired private val transactionTagRepository: TransactionTagRepository,
    @Autowired private val merchantClassificationRuleRepository: MerchantClassificationRuleRepository,
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
                content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.status") { value(400) }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }

        mockMvc
            .get("/transactions") {
                param("payer", "unknown")
                with(allowedOidcLogin())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("UNSUPPORTED_FILTER") }
                jsonPath("$.detail") { value("지원하지 않는 결제자 필터입니다.") }
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
                jsonPath("$.code") { value("INVALID_PAYMENT_DETAILS") }
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
    fun `saves and applies a normalized merchant classification rule within the household`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "김밥천국",
                    saveMerchantRule = true,
                )
            }.andExpect {
                status { isCreated() }
                jsonPath("$.classificationSource") { value("USER") }
            }

        val rule = assertNotNull(
            merchantClassificationRuleRepository.find(
                currentUser.householdId,
                normalizeMerchant("김밥천국"),
            ),
        )

        mockMvc
            .get("/merchant-classification-rules/recommendation") {
                param("merchant", " 김밥 천국! ")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.ruleId") { value(rule.id) }
                jsonPath("$.category") { value("FOOD") }
                jsonPath("$.tags", hasSize<Any>(2))
                jsonPath("$.source") { value("MERCHANT_RULE") }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "김밥 천국!",
                    classificationRuleId = rule.id,
                )
            }.andExpect {
                status { isCreated() }
                jsonPath("$.classificationSource") { value("MERCHANT_RULE") }
                jsonPath("$.classificationConfidence") { value("HIGH") }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "다른가맹점",
                    classificationRuleId = rule.id,
                )
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_CLASSIFICATION_RULE") }
            }
    }

    @Test
    fun `backfills only matching migration transactions in the current household`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val migrated = saveExistingTransaction(
            householdId = currentUser.householdId,
            payerId = currentUser.id,
            merchant = " 김밥 천국! ",
            source = ClassificationSource.MIGRATION,
            legacyCategory = "분식",
        )
        val migratedStandardCategory = saveExistingTransaction(
            householdId = currentUser.householdId,
            payerId = currentUser.id,
            merchant = "김밥천국",
            source = ClassificationSource.MIGRATION,
            legacyCategory = "식비",
            category = TransactionCategory.FOOD,
        )
        val userConfirmed = saveExistingTransaction(
            householdId = currentUser.householdId,
            payerId = currentUser.id,
            merchant = "김밥천국",
            source = ClassificationSource.USER,
        )
        val aiClassified = saveExistingTransaction(
            householdId = currentUser.householdId,
            payerId = currentUser.id,
            merchant = "김밥천국",
            source = ClassificationSource.AI,
        )
        val similarMerchant = saveExistingTransaction(
            householdId = currentUser.householdId,
            payerId = currentUser.id,
            merchant = "김밥천국 강남점",
            source = ClassificationSource.MIGRATION,
            legacyCategory = "분식",
        )
        val otherHouseholdId = createHousehold("다른 집")
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")
        val otherHousehold = saveExistingTransaction(
            householdId = otherHouseholdId,
            payerId = otherUserId,
            merchant = "김밥천국",
            source = ClassificationSource.MIGRATION,
            legacyCategory = "분식",
        )
        val existingIds = listOf(
            migrated,
            migratedStandardCategory,
            userConfirmed,
            aiClassified,
            similarMerchant,
            otherHousehold,
        )
            .map { requireNotNull(it.id) }
        existingIds.forEach { transactionId ->
            transactionTagRepository.replaceAll(transactionId, setOf(TransactionTag.UTILITY))
        }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "김밥천국",
                    saveMerchantRule = true,
                )
            }.andExpect {
                status { isCreated() }
            }

        val updated = requireNotNull(
            transactionRepository.findByIdAndHouseholdId(
                requireNotNull(migrated.id),
                currentUser.householdId,
            ),
        )
        assertEquals(TransactionCategory.FOOD, updated.category)
        assertEquals(ClassificationSource.MERCHANT_RULE, updated.classificationSource)
        assertEquals(ClassificationConfidence.HIGH, updated.classificationConfidence)
        assertNull(updated.classificationConfirmedAt)
        assertTrue(updated.updatedAt > migrated.updatedAt)

        val preserved = listOf(
            migratedStandardCategory,
            userConfirmed,
            aiClassified,
            similarMerchant,
            otherHousehold,
        )
        preserved.forEach { original ->
            val found = requireNotNull(
                transactionRepository.findByIdAndHouseholdId(
                    requireNotNull(original.id),
                    original.householdId,
                ),
            )
            assertEquals(original.category, found.category)
            assertEquals(original.classificationSource, found.classificationSource)
            assertEquals(original.classificationConfidence, found.classificationConfidence)
            assertEquals(
                original.classificationConfirmedAt?.toInstant(),
                found.classificationConfirmedAt?.toInstant(),
            )
            assertEquals(original.updatedAt.toInstant(), found.updatedAt.toInstant())
        }

        val tagsByTransactionId = transactionTagRepository.findAllByTransactionIds(existingIds)
        assertEquals(
            setOf(TransactionTag.SUBSCRIPTION, TransactionTag.RECURRING_PAYMENT),
            tagsByTransactionId[migrated.id],
        )
        preserved.forEach { transaction ->
            assertEquals(setOf(TransactionTag.UTILITY), tagsByTransactionId[transaction.id])
        }
    }

    @Test
    fun `updates an unconfirmed merchant rule backfill when the rule changes`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val migrated = saveExistingTransaction(
            householdId = currentUser.householdId,
            payerId = currentUser.id,
            merchant = "김밥천국",
            source = ClassificationSource.MIGRATION,
            legacyCategory = "분식",
        )

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "김밥천국",
                    saveMerchantRule = true,
                )
            }.andExpect {
                status { isCreated() }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "김밥천국",
                    category = "LIVING",
                    tags = """["UTILITY"]""",
                    saveMerchantRule = true,
                )
            }.andExpect {
                status { isCreated() }
            }

        val updated = requireNotNull(
            transactionRepository.findByIdAndHouseholdId(
                requireNotNull(migrated.id),
                currentUser.householdId,
            ),
        )
        assertEquals(TransactionCategory.LIVING, updated.category)
        assertEquals(ClassificationSource.MERCHANT_RULE, updated.classificationSource)
        assertNull(updated.classificationConfirmedAt)
        assertEquals(
            setOf(TransactionTag.UTILITY),
            transactionTagRepository.findAllByTransactionIds(listOf(requireNotNull(migrated.id)))[migrated.id],
        )
    }

    @Test
    fun `does not expose or apply another household merchant rule`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val otherHouseholdId = createHousehold("다른 집")
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")
        merchantClassificationRuleRepository.upsert(
            householdId = otherHouseholdId,
            merchant = "동네마트",
            normalizedMerchant = normalizeMerchant("동네마트"),
            category = TransactionCategory.LIVING,
            tags = setOf(TransactionTag.UTILITY),
            confirmedByUserId = otherUserId,
            now = now,
        )
        val otherRule = assertNotNull(
            merchantClassificationRuleRepository.find(
                otherHouseholdId,
                normalizeMerchant("동네마트"),
            ),
        )

        mockMvc
            .get("/merchant-classification-rules/recommendation") {
                param("merchant", "동네마트")
                with(allowedOidcLogin())
            }.andExpect {
                status { isNoContent() }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "동네마트",
                    classificationRuleId = otherRule.id,
                )
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_CLASSIFICATION_RULE") }
            }
    }

    @Test
    fun `rejects automatic classification sources without a verified rule`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        listOf("MERCHANT_RULE", "HISTORY", "MIGRATION").forEach { source ->
            mockMvc
                .post("/transactions") {
                    with(allowedOidcLogin())
                    with(csrf())
                    contentType = MediaType.APPLICATION_JSON
                    content = transactionJson(
                        payerId = currentUser.id,
                        merchant = "동네마트",
                        classificationSource = source,
                    )
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.code") { value("INVALID_CLASSIFICATION_SOURCE") }
                }
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
                jsonPath("$.code") { value("TRANSACTION_MODIFIED") }
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
                jsonPath("$.code") { value("TRANSACTION_NOT_FOUND") }
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
        classificationSource: String = "USER",
        classificationRuleId: Long? = null,
        saveMerchantRule: Boolean = false,
        category: String = "FOOD",
        tags: String = """["SUBSCRIPTION", "RECURRING_PAYMENT"]""",
    ) =
        """
        {
          "payerId": $payerId,
          "merchant": "$merchant",
          "description": ${description?.let { "\"$it\"" } ?: "null"},
          "amount": 8000,
          "category": "$category",
          "tags": $tags,
          "classificationSource": "$classificationSource",
          "classificationRuleId": ${classificationRuleId ?: "null"},
          "saveMerchantRule": $saveMerchantRule,
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

    private fun saveExistingTransaction(
        householdId: Long,
        payerId: Long,
        merchant: String,
        source: ClassificationSource,
        legacyCategory: String? = null,
        category: TransactionCategory = TransactionCategory.OTHER,
    ): Transaction =
        transactionRepository.save(
            Transaction(
                householdId = householdId,
                payerId = payerId,
                merchant = merchant,
                description = null,
                amount = 5_000,
                legacyCategory = legacyCategory,
                category = category,
                classificationSource = source,
                classificationConfidence = when (source) {
                    ClassificationSource.USER,
                    ClassificationSource.MERCHANT_RULE,
                    -> ClassificationConfidence.HIGH
                    ClassificationSource.HISTORY -> ClassificationConfidence.MEDIUM
                    ClassificationSource.AI,
                    ClassificationSource.MIGRATION,
                    -> ClassificationConfidence.LOW
                },
                classificationConfirmedAt = now,
                paymentMethod = PaymentMethod.UNKNOWN,
                cardIssuer = null,
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
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
