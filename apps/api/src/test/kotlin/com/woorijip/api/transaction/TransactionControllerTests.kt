package com.woorijip.api.transaction

import com.woorijip.api.TestcontainersConfiguration
import org.hamcrest.Matchers.hasSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
class TransactionControllerTests(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `creates and lists a transaction`() {
        mockMvc
            .post("/transactions") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "merchant": "김밥천국",
                      "amount": 8000,
                      "category": "식비",
                      "occurredAt": "2026-07-15T12:30:00+09:00"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { isNumber() }
                jsonPath("$.merchant") { value("김밥천국") }
                jsonPath("$.amount") { value(8000) }
                jsonPath("$.category") { value("식비") }
            }

        mockMvc
            .get("/transactions")
            .andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(1))
                jsonPath("$[0].merchant") { value("김밥천국") }
            }
    }

    @Test
    fun `rejects invalid transaction fields`() {
        mockMvc
            .post("/transactions") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "merchant": " ",
                      "amount": 0,
                      "category": "",
                      "occurredAt": null
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
            }
    }
}
