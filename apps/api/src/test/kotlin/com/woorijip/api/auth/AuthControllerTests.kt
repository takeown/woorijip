package com.woorijip.api.auth

import com.woorijip.api.TestcontainersConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class AuthControllerTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
) {
    @Test
    fun `returns the current internal user`() {
        val currentUser = googleAccountService.provision(
            TestOidcUsers.allowed(),
        )

        mockMvc
            .get("/auth/me") {
                with(
                    oidcLogin().idToken { token ->
                        token
                            .subject("google-subject-1")
                            .claim("email", "first@example.com")
                            .claim("email_verified", true)
                    },
                )
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(currentUser.id) }
                jsonPath("$.displayName") { value("첫 번째 사용자") }
                jsonPath("$.householdId") { value(currentUser.householdId) }
            }
    }

    @Test
    fun `rejects an anonymous current user request`() {
        mockMvc
            .get("/auth/me")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `issues a CSRF token without authentication`() {
        mockMvc
            .get("/auth/csrf")
            .andExpect {
                status { isOk() }
                jsonPath("$.token") { isNotEmpty() }
                jsonPath("$.headerName") { value("X-XSRF-TOKEN") }
            }
    }
}
