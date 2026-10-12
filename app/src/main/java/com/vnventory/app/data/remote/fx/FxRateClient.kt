package com.vnventory.app.data.remote.fx

import io.ktor.client.HttpClient
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 双源自动链：currency-api 优先（覆盖广、含 TWD），frankfurter 兜底（1999 起的历史）。
 *
 * 结果日期规则（与「等发布后自动定格」一致）：
 * - 数据源返回的日期等于请求日期 → 直接采用；
 * - 只拿到更早日期的汇率时，周末 / 较早日期视为正常的最近交易日回退；
 *   近两天的工作日回退视为「尚未发布」，保持待定格等待自动重试，不用邻近日期冒充。
 */
class FxRateClient(
    private val sources: List<FxRateSource>,
    private val today: () -> LocalDate = LocalDate::now,
) {

    suspend fun fetch(from: String, to: String, date: LocalDate): FxFetchResult {
        var fallback: FxFetchResult.Success? = null
        var sawNotAvailable = false
        var sawUnsupported = false
        for (source in sources) {
            when (val result = source.fetch(from, to, date)) {
                is FxFetchResult.Success -> {
                    if (result.rateDate == date) return result
                    if (fallback == null || result.rateDate > fallback.rateDate) fallback = result
                }

                FxFetchResult.NotAvailable -> sawNotAvailable = true
                FxFetchResult.Unsupported -> sawUnsupported = true
                FxFetchResult.Failed -> Unit
            }
        }
        fallback?.let { best ->
            return if (acceptFallback(best.rateDate, date)) best else FxFetchResult.NotAvailable
        }
        return when {
            sawNotAvailable -> FxFetchResult.NotAvailable
            sawUnsupported -> FxFetchResult.Unsupported
            else -> FxFetchResult.Failed
        }
    }

    private fun acceptFallback(effective: LocalDate, requested: LocalDate): Boolean {
        if (effective >= requested) return true
        val weekend = requested.dayOfWeek == DayOfWeek.SATURDAY || requested.dayOfWeek == DayOfWeek.SUNDAY
        return weekend || requested <= today().minusDays(RECENT_WEEKDAY_DAYS)
    }

    companion object {
        private const val RECENT_WEEKDAY_DAYS = 2L

        fun default(client: HttpClient): FxRateClient = FxRateClient(
            listOf(CurrencyApiSource(client), FrankfurterSource(client)),
        )
    }
}
