package com.woorijip.api.statement

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.auth.TestOidcUsers
import com.woorijip.api.transaction.CardIssuer
import com.woorijip.api.transaction.PaymentMethod
import com.woorijip.api.transaction.Transaction
import com.woorijip.api.transaction.TransactionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class CardStatementControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `previews a valid KB statement without saving the source file`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val matchedTransactionId = assertNotNull(
            transactionRepository.save(
                Transaction(
                    householdId = currentUser.householdId,
                    payerId = currentUser.id,
                    merchant = "세븐일레븐 테스트점",
                    description = null,
                    amount = 2_300,
                    category = "생활",
                    paymentMethod = PaymentMethod.CARD,
                    cardIssuer = CardIssuer.KB_KOOKMIN,
                    occurredAt = OffsetDateTime.parse("2026-06-09T12:00:00+09:00"),
                    createdAt = OffsetDateTime.parse("2026-07-01T00:00:00+09:00"),
                ),
            ).id,
        )

        mockMvc
            .multipart("/card-statements/preview") {
                file(validStatement())
                with(allowedOidcLogin())
                with(csrf())
            }.andExpect {
                status { isOk() }
                jsonPath("$.cardIssuer") { value("KB_KOOKMIN") }
                jsonPath("$.statementMonth") { value("2026-07") }
                jsonPath("$.totalCount") { value(3) }
                jsonPath("$.totalBilledAmount") { value(109277) }
                jsonPath("$.adjustmentCount") { value(2) }
                jsonPath("$.matchedCount") { value(1) }
                jsonPath("$.missingCount") { value(2) }
                jsonPath("$.duplicateSuspectedCount") { value(0) }
                jsonPath("$.mismatchCount") { value(0) }
                jsonPath("$.candidates.length()") { value(3) }
                jsonPath("$.candidates[0].merchant") { value("세븐일레븐 테스트점") }
                jsonPath("$.candidates[0].matchStatus") { value("MATCHED") }
                jsonPath("$.candidates[0].transactionIds[0]") { value(matchedTransactionId) }
                jsonPath("$.candidates[1].type") { value("INSTALLMENT") }
                jsonPath("$.candidates[1].matchStatus") { value("MISSING") }
                jsonPath("$.candidates[2].type") { value("FEE") }
                jsonPath("$.candidates[2].matchStatus") { value("MISSING") }
            }

        assertEquals(1, transactionRepository.count())
    }

    @Test
    fun `rejects unsupported files and requires authentication and CSRF`() {
        googleAccountService.provision(TestOidcUsers.allowed())
        val unsupportedFile = MockMultipartFile(
            "file",
            "statement.xls",
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            "not a spreadsheet".toByteArray(),
        )

        mockMvc
            .multipart("/card-statements/preview") {
                file(unsupportedFile)
                with(allowedOidcLogin())
                with(csrf())
            }.andExpect {
                status { isBadRequest() }
            }

        mockMvc
            .multipart("/card-statements/preview") {
                file(validStatement())
                with(csrf())
            }.andExpect {
                status { isUnauthorized() }
            }

        mockMvc
            .multipart("/card-statements/preview") {
                file(validStatement())
                with(allowedOidcLogin())
            }.andExpect {
                status { isForbidden() }
            }
    }

    private fun validStatement() = MockMultipartFile(
        "file",
        "kb-statement.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        KbStatementTestWorkbook.create(),
    )

    private fun allowedOidcLogin() = oidcLogin().oidcUser(TestOidcUsers.allowed())
}
