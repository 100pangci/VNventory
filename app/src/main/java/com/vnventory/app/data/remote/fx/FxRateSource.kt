package com.vnventory.app.data.remote.fx

import java.time.LocalDate

/**
 * 一次汇率查询的结果。
 *
 * [Success.rateScaled] 表示「1 单位 from 币 = rateScaled / 10^9 单位 to 币」；
 * [Success.rateDate] 是数据源实际采用的汇率日期（可能与请求日期不同，见 [FxRateClient] 的回退规则）。
 */
sealed interface FxFetchResult {
    data class Success(val rateScaled: Long, val rateDate: LocalDate, val source: String) : FxFetchResult

    /** 目标日期汇率尚未发布，可以稍后重试。 */
    data object NotAvailable : FxFetchResult

    /** 数据源不支持该币种或该历史范围，重试也不会出现。 */
    data object Unsupported : FxFetchResult

    /** 网络或解析失败。 */
    data object Failed : FxFetchResult
}

interface FxRateSource {
    val name: String

    suspend fun fetch(from: String, to: String, date: LocalDate): FxFetchResult
}
