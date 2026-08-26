package com.woorijip.api.statistics

import com.woorijip.api.auth.CurrentUser
import com.woorijip.api.error.ApiException
import com.woorijip.api.error.ErrorCode
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class SpendingStatisticsController(
    private val spendingStatisticsService: SpendingStatisticsService,
) {
    @GetMapping("/statistics/spending")
    fun find(
        currentUser: CurrentUser,
        @RequestParam(defaultValue = "MONTH") period: String,
        @RequestParam(defaultValue = "ALL") payer: String,
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(defaultValue = "false") includeMonthlySummary: Boolean,
        @RequestParam(defaultValue = "false") includeDailyBreakdown: Boolean,
        @RequestParam(defaultValue = "false") includeDailyTransactions: Boolean,
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
        return if (date == null) {
            spendingStatisticsService.find(
                currentUser,
                spendingPeriod,
                spendingPayer,
                includeMonthlySummary = includeMonthlySummary,
                includeDailyBreakdown = includeDailyBreakdown,
                includeDailyTransactions = includeDailyTransactions,
            )
        } else {
            spendingStatisticsService.find(
                currentUser,
                spendingPeriod,
                spendingPayer,
                referenceDate = date,
                includeMonthlySummary = includeMonthlySummary,
                includeDailyBreakdown = includeDailyBreakdown,
                includeDailyTransactions = includeDailyTransactions,
            )
        }
    }
}
