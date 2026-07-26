package com.woorijip.api.statistics

import com.woorijip.api.auth.GoogleAccountService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@RestController
class SpendingStatisticsController(
    private val googleAccountService: GoogleAccountService,
    private val spendingStatisticsService: SpendingStatisticsService,
) {
    @GetMapping("/statistics/spending")
    fun find(
        @AuthenticationPrincipal oidcUser: OidcUser,
        @RequestParam(defaultValue = "MONTH") period: String,
        @RequestParam(required = false) date: LocalDate?,
    ): SpendingStatistics {
        val spendingPeriod = try {
            SpendingPeriod.valueOf(period.uppercase())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 통계 기간입니다.")
        }
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
        return if (date == null) {
            spendingStatisticsService.find(currentUser, spendingPeriod)
        } else {
            spendingStatisticsService.find(currentUser, spendingPeriod, date)
        }
    }
}
