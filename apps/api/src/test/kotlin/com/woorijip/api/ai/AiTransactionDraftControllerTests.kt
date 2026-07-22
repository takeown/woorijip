package com.woorijip.api.ai

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.auth.TestOidcUsers
import com.woorijip.api.household.HouseholdMembership
import com.woorijip.api.household.HouseholdMembershipRepository
import com.woorijip.api.identity.AppUser
import com.woorijip.api.identity.AppUserRepository
import com.woorijip.api.transaction.TransactionRepository
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
                jsonPath("$.amount") { value(8000) }
                jsonPath("$.category") { value("식비") }
                jsonPath("$.payerId") { value(currentUser.id) }
            }

        assertEquals(0, transactionRepository.count())
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
                message.contains("배우자") -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.READY,
                    merchant = "이마트",
                    amount = 30_000,
                    category = "생활",
                    occurredAt = "2026-07-21T12:30:00+09:00",
                    payer = GeneratedPayer.PARTNER,
                )
                else -> GeneratedTransactionDraft(
                    status = GeneratedDraftStatus.READY,
                    merchant = "김밥천국",
                    amount = 8_000,
                    category = "식비",
                    occurredAt = "2026-07-21T12:30:00+09:00",
                    payer = GeneratedPayer.ME,
                )
            }
        }
}
