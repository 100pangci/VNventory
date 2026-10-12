package com.vnventory.app.domain.model

/**
 * 年度预算设置（DataStore 与备份共用）。
 *
 * 金额为最小货币单位的 Long；[currency] 为空表示跟随当前「默认货币」。
 * [yearlyAmounts] 按年份单独记录预算金额（可预填未来年份）。
 */
data class BudgetPreferences(
    val enabled: Boolean = false,
    val currency: String? = null,
    val yearlyAmounts: Map<Int, Long> = emptyMap(),
    /** true = 含运费 / 手续费；false = 只统计盒本体价。 */
    val includeFees: Boolean = true,
) {
    fun amountForYear(year: Int): Long? = yearlyAmounts[year]
}
