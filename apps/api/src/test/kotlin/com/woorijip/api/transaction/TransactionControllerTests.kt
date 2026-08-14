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
import com.woorijip.api.storedvalue.StoredValueAccountRepository
import com.woorijip.api.storedvalue.StoredValueAccountCategory
import com.woorijip.api.storedvalue.StoredValueAutomationKey
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
import tools.jackson.databind.ObjectMapper
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
    @Autowired private val storedValueAccountRepository: StoredValueAccountRepository,
    @Autowired private val objectMapper: ObjectMapper,
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
                jsonPath("$.items", hasSize<Any>(2))
                jsonPath("$.nextCursor") { doesNotExist() }
            }

        mockMvc
            .get("/transactions") {
                param("payer", "me")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.items", hasSize<Any>(1))
                jsonPath("$.items[0].merchant") { value("김밥천국") }
                jsonPath("$.items[0].description") { value("점심 식사") }
                jsonPath("$.items[0].payerId") { value(currentUser.id) }
                jsonPath("$.items[0].category") { value("FOOD") }
                jsonPath("$.items[0].tags", hasSize<Any>(2))
                jsonPath("$.items[0].classificationSource") { value("USER") }
                jsonPath("$.items[0].classificationConfidence") { value("HIGH") }
                jsonPath("$.items[0].classificationConfirmedAt") { exists() }
            }

        mockMvc
            .get("/transactions") {
                param("payer", "partner")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.items", hasSize<Any>(1))
                jsonPath("$.items[0].merchant") { value("동네마트") }
                jsonPath("$.items[0].payerId") { value(partnerId) }
            }
    }

    @Test
    fun `searches transaction text within a Seoul date range and payer filter`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val partnerId = createMember(currentUser.householdId, "배우자")
        val otherHouseholdId = createHousehold("다른 집")
        val otherUserId = createMember(otherHouseholdId, "다른 사용자")

        createTransaction(currentUser.id, "쿠팡", occurredAt = "2026-08-15T12:00:00+09:00")
        createTransaction(partnerId, "동네마트", "쿠팡 기저귀", "2026-08-01T00:00:00+09:00")
        createTransaction(partnerId, "쿠팡", occurredAt = "2026-07-31T23:59:59+09:00")
        createTransaction(partnerId, "쿠팡", occurredAt = "2026-09-01T00:00:00+09:00")
        createTransaction(partnerId, "100% 할인점", occurredAt = "2026-08-20T12:00:00+09:00")
        transactionRepository.save(
            Transaction(
                householdId = otherHouseholdId,
                payerId = otherUserId,
                merchant = "쿠팡 다른 집",
                description = null,
                amount = 10_000,
                category = TransactionCategory.FOOD,
                paymentMethod = PaymentMethod.CARD,
                cardIssuer = CardIssuer.SHINHAN,
                occurredAt = OffsetDateTime.parse("2026-08-15T12:00:00+09:00"),
                createdAt = now,
            ),
        )

        mockMvc
            .get("/transactions") {
                param("payer", "partner")
                param("q", " 쿠팡 ")
                param("from", "2026-08-01")
                param("to", "2026-08-31")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.items", hasSize<Any>(1))
                jsonPath("$.items[0].merchant") { value("동네마트") }
                jsonPath("$.items[0].description") { value("쿠팡 기저귀") }
                jsonPath("$.items[0].payerId") { value(partnerId) }
            }

        mockMvc
            .get("/transactions") {
                param("payer", "partner")
                param("q", "%")
                param("from", "2026-08-01")
                param("to", "2026-08-31")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.items", hasSize<Any>(1))
                jsonPath("$.items[0].merchant") { value("100% 할인점") }
            }
    }

    @Test
    fun `paginates transactions with a stable cursor when occurred times are equal`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        createTransaction(currentUser.id, "첫 번째")
        createTransaction(currentUser.id, "두 번째")
        createTransaction(currentUser.id, "세 번째")

        val firstPage = mockMvc
            .get("/transactions") {
                param("size", "2")
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.items", hasSize<Any>(2))
                jsonPath("$.items[0].merchant") { value("세 번째") }
                jsonPath("$.items[1].merchant") { value("두 번째") }
                jsonPath("$.nextCursor") { isNotEmpty() }
            }.andReturn()

        val nextCursor = objectMapper
            .readTree(firstPage.response.contentAsString)
            .path("nextCursor")
            .asString()

        mockMvc
            .get("/transactions") {
                param("size", "2")
                param("cursor", nextCursor)
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$.items", hasSize<Any>(1))
                jsonPath("$.items[0].merchant") { value("첫 번째") }
                jsonPath("$.nextCursor") { doesNotExist() }
            }
    }

    @Test
    fun `rejects invalid transaction cursors and page sizes`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .get("/transactions") {
                param("cursor", "invalid")
                with(allowedOidcLogin())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.detail") { value("올바르지 않은 거래 조회 커서입니다.") }
            }

        listOf("0", "101").forEach { size ->
            mockMvc
                .get("/transactions") {
                    param("size", size)
                    with(allowedOidcLogin())
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.code") { value("INVALID_REQUEST") }
                }
        }

        mockMvc
            .get("/transactions") {
                param("q", "가".repeat(101))
                with(allowedOidcLogin())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.detail") { value("검색어는 100자 이하로 입력해 주세요.") }
            }

        mockMvc
            .get("/transactions") {
                param("from", "2026-08-02")
                param("to", "2026-08-01")
                with(allowedOidcLogin())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
                jsonPath("$.detail") { value("시작 날짜는 종료 날짜보다 늦을 수 없습니다.") }
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
                jsonPath("$.items", hasSize<Any>(1))
                jsonPath("$.items[0].merchant") { value("우리 집 거래") }
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
    fun `charges and spends an Onnuri balance through a registered card`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val account = createStoredValueAccount(currentUser.householdId, currentUser.id)

        mockMvc
            .post("/stored-value-accounts/${account.id}/credits") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "balanceAmount": 10000,
                      "paidAmount": 9300,
                      "sourceName": "생활비 계좌",
                      "occurredAt": "2026-08-03T12:00:00+09:00"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.balance") { value(10_000) }
            }

        val created = mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "GS25",
                    cardIssuer = "HYUNDAI",
                    amount = 2_400,
                    storedValueAccountId = account.id,
                )
            }.andExpect {
                status { isCreated() }
                jsonPath("$.amount") { value(2_400) }
                jsonPath("$.paymentMethod") { value("CARD") }
                jsonPath("$.cardIssuer") { value("HYUNDAI") }
                jsonPath("$.storedValueAccountId") { value(account.id) }
            }.andReturn()

        mockMvc
            .get("/stored-value-accounts") {
                with(allowedOidcLogin())
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].automationKey") { value("ONNURI_GIFT_CERTIFICATE") }
                jsonPath("$[0].balance") { value(7_600) }
            }

        val createdJson = objectMapper.readTree(created.response.contentAsString)
        mockMvc
            .delete("/transactions/${createdJson.path("id").asLong()}") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"expectedUpdatedAt":"${createdJson.path("updatedAt").asString()}"}"""
            }.andExpect {
                status { isNoContent() }
            }

        assertEquals(10_000, storedValueAccountRepository.findAllByHouseholdId(currentUser.householdId)[0].balance)
    }

    @Test
    fun `rejects QR spending without an account and spending beyond its balance`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val account = createStoredValueAccount(currentUser.householdId, currentUser.id)

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(currentUser.id, "QR 결제", paymentMethod = "QR", cardIssuer = null)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_STORED_VALUE_ACCOUNT") }
            }

        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = currentUser.id,
                    merchant = "잔액 초과",
                    paymentMethod = "QR",
                    cardIssuer = null,
                    storedValueAccountId = account.id,
                )
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INSUFFICIENT_STORED_VALUE_BALANCE") }
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
        occurredAt: String = "2026-07-15T12:30:00+09:00",
    ) {
        mockMvc
            .post("/transactions") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = transactionJson(
                    payerId = payerId,
                    merchant = merchant,
                    description = description,
                    occurredAt = occurredAt,
                )
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
        amount: Long = 8_000,
        storedValueAccountId: Long? = null,
        occurredAt: String = "2026-07-15T12:30:00+09:00",
    ) =
        """
        {
          "payerId": $payerId,
          "merchant": "$merchant",
          "description": ${description?.let { "\"$it\"" } ?: "null"},
          "amount": $amount,
          "category": "$category",
          "tags": $tags,
          "classificationSource": "$classificationSource",
          "classificationRuleId": ${classificationRuleId ?: "null"},
          "saveMerchantRule": $saveMerchantRule,
          "paymentMethod": "$paymentMethod",
          "cardIssuer": ${cardIssuer?.let { "\"$it\"" } ?: "null"},
          "storedValueAccountId": ${storedValueAccountId ?: "null"},
          "occurredAt": "$occurredAt"
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

    private fun createStoredValueAccount(householdId: Long, ownerUserId: Long) =
        storedValueAccountRepository.create(
            householdId = householdId,
            ownerUserId = ownerUserId,
            name = "온누리상품권",
            category = StoredValueAccountCategory.GIFT_CERTIFICATE,
            automationKey = StoredValueAutomationKey.ONNURI_GIFT_CERTIFICATE,
            createdAt = now,
        ).let { accountId ->
            requireNotNull(storedValueAccountRepository.findByIdAndHouseholdIdForUpdate(accountId, householdId))
        }

    private fun allowedOidcLogin() = oidcLogin().oidcUser(TestOidcUsers.allowed())

    private companion object {
        val now: OffsetDateTime = OffsetDateTime.parse("2026-07-15T12:30:00+09:00")
    }
}
