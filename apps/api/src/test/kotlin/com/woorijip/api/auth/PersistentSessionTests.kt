package com.woorijip.api.auth

import com.woorijip.api.TestcontainersConfiguration
import jakarta.servlet.http.HttpSession
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
    properties = [
        "app.auth.allowed-google-emails=first@example.com,second@example.com",
        "server.servlet.session.cookie.secure=false",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, PersistentSessionTestConfiguration::class)
class PersistentSessionTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val googleAccountService: GoogleAccountService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {
    @Test
    fun `stores and reuses authentication with a persistent session cookie`() {
        val currentUser = googleAccountService.provision(TestOidcUsers.allowed())

        val authenticated = mockMvc
            .get("/oauth2/session-test-login")
            .andExpect {
                status { isOk() }
            }.andReturn()

        val sessionCookie = assertNotNull(authenticated.response.getCookie("SESSION"))
        assertEquals(Duration.ofDays(30).seconds.toInt(), sessionCookie.maxAge)
        assertTrue(sessionCookie.isHttpOnly)
        assertFalse(sessionCookie.secure)
        val sessionId = String(
            Base64.getDecoder().decode(sessionCookie.value),
            StandardCharsets.UTF_8,
        )

        assertEquals(
            Duration.ofDays(30).seconds.toInt(),
            jdbcTemplate.queryForObject(
                "SELECT max_inactive_interval FROM spring_session WHERE session_id = ?",
                Int::class.java,
                sessionId,
            ),
        )

        mockMvc
            .get("/auth/me") {
                cookie(sessionCookie)
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(currentUser.id) }
            }

        mockMvc
            .post("/auth/logout") {
                cookie(sessionCookie)
                with(csrf())
            }.andExpect {
                status { isNoContent() }
            }

        mockMvc
            .get("/auth/me") {
                cookie(sessionCookie)
            }.andExpect {
                status { isUnauthorized() }
            }
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Int::class.java,
                sessionId,
            ),
        )
    }

    @Test
    fun `invalidates a persistent session when the Google account is no longer allowed`() {
        val authenticated = mockMvc
            .get("/oauth2/session-test-login/disallowed")
            .andExpect {
                status { isOk() }
            }.andReturn()

        val sessionCookie = assertNotNull(authenticated.response.getCookie("SESSION"))
        val sessionId = String(
            Base64.getDecoder().decode(sessionCookie.value),
            StandardCharsets.UTF_8,
        )

        val denied = mockMvc
            .get("/auth/me") {
                cookie(sessionCookie)
            }.andExpect {
                status { isUnauthorized() }
            }.andReturn()

        assertEquals(0, assertNotNull(denied.response.getCookie("SESSION")).maxAge)
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Int::class.java,
                sessionId,
            ),
        )
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PersistentSessionTestConfiguration {
    @Bean
    fun persistentSessionTestController() = PersistentSessionTestController()
}

@RestController
class PersistentSessionTestController {
    @GetMapping("/oauth2/session-test-login")
    fun login(session: HttpSession) {
        val principal = TestOidcUsers.allowed()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = OAuth2AuthenticationToken(principal, principal.authorities, "google")
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
    }

    @GetMapping("/oauth2/session-test-login/disallowed")
    fun loginWithDisallowedAccount(session: HttpSession) {
        val principal = TestOidcUsers.disallowed()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = OAuth2AuthenticationToken(principal, principal.authorities, "google")
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context)
    }
}
