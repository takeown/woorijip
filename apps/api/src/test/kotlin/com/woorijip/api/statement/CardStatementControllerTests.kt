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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import tools.jackson.databind.ObjectMapper
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
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val objectMapper: ObjectMapper,
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
                jsonPath("$.importId") { isNumber() }
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
        assertEquals(1, countRows("card_statement_imports"))
        assertEquals(3, countRows("card_statement_candidates"))
    }

    @Test
    fun `reuses a normalized import and applies a missing candidate once`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        val firstPreview = previewStatement()
        val secondPreview = previewStatement()
        val firstJson = objectMapper.readTree(firstPreview.response.contentAsString)
        val secondJson = objectMapper.readTree(secondPreview.response.contentAsString)
        val importId = firstJson.path("importId").longValue()
        val sourceRow = firstJson.path("candidates").first().path("sourceRow").intValue()

        assertEquals(importId, secondJson.path("importId").longValue())
        assertEquals(1, countRows("card_statement_imports"))
        assertEquals(3, countRows("card_statement_candidates"))

        mockMvc
            .post("/card-statements/$importId/apply") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "candidates": [
                        {
                          "sourceRow": $sourceRow,
                          "category": "생활",
                          "description": "명세서 확인"
                        }
                      ]
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.transactions[0].sourceRow") { value(sourceRow) }
                jsonPath("$.transactions[0].created") { value(true) }
                jsonPath("$.transactions[0].transactionId") { isNumber() }
            }

        val savedTransaction = transactionRepository.findAll().single()
        assertEquals("세븐일레븐 테스트점", savedTransaction.merchant)
        assertEquals(2_300, savedTransaction.amount)
        assertEquals("생활", savedTransaction.category)
        assertEquals("명세서 확인", savedTransaction.description)
        assertEquals(PaymentMethod.CARD, savedTransaction.paymentMethod)
        assertEquals(CardIssuer.KB_KOOKMIN, savedTransaction.cardIssuer)
        assertEquals(
            OffsetDateTime.parse("2026-06-09T12:00:00+09:00").toInstant(),
            savedTransaction.occurredAt.toInstant(),
        )

        mockMvc
            .post("/card-statements/$importId/apply") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "candidates": [
                        {
                          "sourceRow": $sourceRow,
                          "category": "생활",
                          "description": "명세서 확인"
                        }
                      ]
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.transactions[0].created") { value(false) }
                jsonPath("$.transactions[0].transactionId") {
                    value(requireNotNull(savedTransaction.id))
                }
            }

        assertEquals(1, transactionRepository.count())
    }

    @Test
    fun `rejects a candidate that is no longer missing at apply time`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())
        val previewJson = objectMapper.readTree(previewStatement().response.contentAsString)
        val importId = previewJson.path("importId").longValue()
        val sourceRow = previewJson.path("candidates").first().path("sourceRow").intValue()
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
        )

        mockMvc
            .post("/card-statements/$importId/apply") {
                with(allowedOidcLogin())
                with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "candidates": [
                        {
                          "sourceRow": $sourceRow,
                          "category": "생활"
                        }
                      ]
                    }
                """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
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

    private fun previewStatement() =
        mockMvc
            .multipart("/card-statements/preview") {
                file(validStatement())
                with(allowedOidcLogin())
                with(csrf())
            }.andExpect {
                status { isOk() }
            }.andReturn()

    private fun countRows(table: String): Int =
        requireNotNull(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java))

    private fun allowedOidcLogin() = oidcLogin().oidcUser(TestOidcUsers.allowed())
}
