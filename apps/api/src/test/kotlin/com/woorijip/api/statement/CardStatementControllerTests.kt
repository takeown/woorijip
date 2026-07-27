package com.woorijip.api.statement

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.auth.TestOidcUsers
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
import kotlin.test.Test

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CardStatementControllerTests(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `previews a valid KB statement without saving the source file`() {
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
                jsonPath("$.candidates.length()") { value(3) }
                jsonPath("$.candidates[0].merchant") { value("세븐일레븐 테스트점") }
                jsonPath("$.candidates[1].type") { value("INSTALLMENT") }
                jsonPath("$.candidates[2].type") { value("FEE") }
            }
    }

    @Test
    fun `rejects unsupported files and requires authentication and CSRF`() {
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
