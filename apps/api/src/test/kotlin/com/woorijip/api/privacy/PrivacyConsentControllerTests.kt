package com.woorijip.api.privacy

import com.woorijip.api.TestcontainersConfiguration
import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.auth.TestOidcUsers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test

@SpringBootTest(properties = ["app.auth.allowed-google-emails=first@example.com,second@example.com"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class PrivacyConsentControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
) {
    @Test
    fun `records required and optional consent separately and allows optional withdrawal`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        mockMvc.get("/auth/privacy-consents") { with(login()) }.andExpect {
            status { isOk() }
            jsonPath("$.privacyPolicyAgreed") { value(false) }
            jsonPath("$.aiOverseasTransferAgreed") { value(false) }
        }

        update(privacy = true, ai = true).andExpect {
            status { isOk() }
            jsonPath("$.privacyPolicyVersion") { value("2026-09-09") }
            jsonPath("$.privacyPolicyAgreed") { value(true) }
            jsonPath("$.privacyPolicyAgreedAt") { isNotEmpty() }
            jsonPath("$.aiOverseasTransferAgreed") { value(true) }
            jsonPath("$.aiOverseasTransferAgreedAt") { isNotEmpty() }
        }

        update(privacy = true, ai = false).andExpect {
            status { isOk() }
            jsonPath("$.privacyPolicyAgreed") { value(true) }
            jsonPath("$.aiOverseasTransferAgreed") { value(false) }
            jsonPath("$.aiOverseasTransferAgreedAt") { doesNotExist() }
        }
    }

    @Test
    fun `rejects a request without required privacy consent`() {
        googleAccountService.provision(TestOidcUsers.allowed())

        update(privacy = false, ai = true).andExpect {
            status { isBadRequest() }
        }
    }

    private fun update(privacy: Boolean, ai: Boolean) =
        mockMvc.put("/auth/privacy-consents") {
            with(login())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"privacyPolicyAgreed":$privacy,"aiOverseasTransferAgreed":$ai}"""
        }

    private fun login() = oidcLogin().oidcUser(TestOidcUsers.allowed())
}
