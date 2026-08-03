package com.woorijip.api.ai

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.auth.TestOidcUsers
import com.woorijip.api.household.HouseholdMembership
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
import com.woorijip.api.storedvalue.StoredValueAccountType
import com.woorijip.api.transaction.TransactionRepository
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.TransactionCategory
import org.hamcrest.Matchers.containsString
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
        "app.auth.bootstrap-household-name=AI 테스트 우리집",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, AiTransactionDraftTestConfiguration::class)
@Transactional
class AiTransactionDraftControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val appUserRepository: AppUserRepository,
    @Autowired private val householdMembershipRepository: HouseholdMembershipRepository,
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `creates a draft without saving a transaction`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["김밥천국 8천원"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("READY") }
                jsonPath("$.merchant") { value("김밥천국") }
                jsonPath("$.description") { value("점심 식사") }
                jsonPath("$.amount") { value(8000) }
                jsonPath("$.category") { value("FOOD") }
                jsonPath("$.payerId") { value(currentUser.id) }
                jsonPath("$.paymentMethod") { value("CARD") }
                jsonPath("$.cardIssuer") { value("SHINHAN") }
            }

        assertEquals(0, transactionRepository.count())
    }

    @Test
    fun `creates an Onnuri QR draft without asking whether it is card or cash`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["GS25에서 온누리상품권 QR로 기저귀 24000원 결제했어"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("READY") }
                jsonPath("$.paymentMethod") { value("QR") }
                jsonPath("$.cardIssuer") { doesNotExist() }
                jsonPath("$.storedValueAccountType") { value("ONNURI_GIFT_CERTIFICATE") }
            }

        assertEquals(0, transactionRepository.count())
    }

    @Test
    fun `keeps the card issuer and Onnuri balance separate for a linked card draft`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["현대카드에 연결한 온누리상품권으로 24000원 결제했어"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("READY") }
                jsonPath("$.paymentMethod") { value("CARD") }
                jsonPath("$.cardIssuer") { value("HYUNDAI") }
                jsonPath("$.storedValueAccountType") { value("ONNURI_GIFT_CERTIFICATE") }
            }
    }

    @Test
    fun `maps a partner payer within the current household`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val partnerId = createMember(currentUser.householdId, "배우자")

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["배우자가 이마트에서 3만원"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("READY") }
                jsonPath("$.payerId") { value(partnerId) }
                jsonPath("$.payerDisplayName") { value("배우자") }
                jsonPath("$.description") { doesNotExist() }
            }
    }

    @Test
    fun `rejects unsupported and incomplete generated drafts`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["이번 달 식비 알려줘"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UNSUPPORTED") }
                jsonPath("$.message") { value("현재는 거래 한 건 입력만 도와드릴 수 있습니다.") }
            }

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["금액 없는 거래"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NEEDS_CLARIFICATION") }
            }
    }

    @Test
    fun `combines follow-up answers into a completed draft`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["금액 없는 거래","8천원"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("READY") }
                jsonPath("$.amount") { value(8000) }
            }

        assertEquals(0, transactionRepository.count())
    }

    @Test
    fun `asks for clarification when generated payment details are incomplete`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["결제수단 없는 거래"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("NEEDS_CLARIFICATION") }
                jsonPath("$.message") { value(containsString("결제수단")) }
            }
    }

    @Test
    fun `validates the request and requires authentication`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":[" "]}"""
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["하나","둘","셋","넷"]}"""
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .post("/ai/transaction-drafts") {
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["김밥천국 8천원"]}"""
            }.andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `rejects sensitive data before generating a draft`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc
            .post("/ai/transaction-drafts") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"messages":["카드번호 4111-1111-1111-1111로 결제했어"]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("SENSITIVE_AI_INPUT") }
                jsonPath("$.detail") { value(containsString("카드번호")) }
            }
    }

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
        val now: OffsetDateTime = OffsetDateTime.parse("2026-07-21T12:30:00+09:00")
    }
}

@TestConfiguration(proxyBeanMethods = false)
class AiTransactionDraftTestConfiguration {
    @Bean
    @Primary
    fun transactionDraftGenerator(): TransactionDraftGenerator =
        TransactionDraftGenerator { message, _ ->
            when {
                message.contains("알려줘") -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.UNSUPPORTED,
                )
                message.contains("금액 없는") && !message.contains("8천원") -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.NEEDS_CLARIFICATION,
                    message = "금액이 얼마였나요?",
                )
                message.contains("결제수단 없는") -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.READY,
                    merchant = "김밥천국",
                    amount = 8_000,
                    category = TransactionCategory.FOOD,
                    occurredAt = "2026-07-21T12:30:00+09:00",
                    payer = GeneratedPayer.ME,
                )
                message.contains("배우자") -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.READY,
                    merchant = "이마트",
                    amount = 30_000,
                    category = TransactionCategory.LIVING,
                    occurredAt = "2026-07-21T12:30:00+09:00",
                    payer = GeneratedPayer.PARTNER,
                    paymentMethod = PaymentMethod.CASH,
                )
                message.contains("온누리상품권 QR") -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.READY,
                    merchant = "GS25",
                    description = "기저귀",
                    amount = 24_000,
                    category = TransactionCategory.CHILDCARE,
                    occurredAt = "2026-07-21T12:30:00+09:00",
                    payer = GeneratedPayer.ME,
                    paymentMethod = PaymentMethod.QR,
                    storedValueAccountType = StoredValueAccountType.ONNURI_GIFT_CERTIFICATE,
                )
                message.contains("현대카드에 연결한 온누리상품권") -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.READY,
                    merchant = "테스트 가맹점",
                    amount = 24_000,
                    category = TransactionCategory.LIVING,
                    occurredAt = "2026-07-21T12:30:00+09:00",
                    payer = GeneratedPayer.ME,
                    paymentMethod = PaymentMethod.CARD,
                    cardIssuer = CardIssuer.HYUNDAI,
                    storedValueAccountType = StoredValueAccountType.ONNURI_GIFT_CERTIFICATE,
                )
                else -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.READY,
                    merchant = "김밥천국",
                    description = "점심 식사",
                    amount = 8_000,
                    category = TransactionCategory.FOOD,
                    occurredAt = "2026-07-21T12:30:00+09:00",
                    payer = GeneratedPayer.ME,
                    paymentMethod = PaymentMethod.CARD,
                    cardIssuer = CardIssuer.SHINHAN,
                )
            }
        }
}
