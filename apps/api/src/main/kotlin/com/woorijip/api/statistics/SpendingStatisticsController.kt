package com.woorijip.api.statistics

import com.woorijip.api.auth.GoogleAccountService
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
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
        @RequestParam(defaultValue = "ALL") payer: String,
        @RequestParam(required = false) date: LocalDate?,
    ): SpendingStatistics {
        val spendingPeriod = try {
            SpendingPeriod.valueOf(period.uppercase())
        } catch (_: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNSUPPORTED_FILTER, "지원하지 않는 통계 기간입니다.")
        }
        val spendingPayer = try {
            SpendingPayer.valueOf(payer.uppercase())
        } catch (_: IllegalArgumentException) {
            throw ApiException(ErrorCode.UNSUPPORTED_FILTER, "지원하지 않는 결제자 필터입니다.")
        }
        val currentUser = googleAccountService.findByGoogleSubject(oidcUser.subject)
        return if (date == null) {
            spendingStatisticsService.find(currentUser, spendingPeriod, spendingPayer)
        } else {
            spendingStatisticsService.find(currentUser, spendingPeriod, spendingPayer, date)
        }
    }
}
