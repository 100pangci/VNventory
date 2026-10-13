package com.vnventory.app.domain.cost

import com.vnventory.app.domain.model.Fx
import com.vnventory.app.domain.model.Money

/** 首页累计消费统计，不依赖年度预算是否启用或是否设置限额。 */
data class HomeSpendingSummary(
    val currency: String,
    val year: Int,
    val allTimeMinor: Long = 0,
    val allTimeRecordCount: Int = 0,
    val allTimeUnconvertedCount: Int = 0,
    val currentYearMinor: Long = 0,
    val currentYearRecordCount: Int = 0,
    val currentYearUnconvertedCount: Int = 0,
    val currentYearUnpricedCount: Int = 0,
) {
    companion object {
        fun empty(year: Int = java.time.LocalDate.now().year, currency: String = "CNY") =
            HomeSpendingSummary(currency = currency, year = year)
    }
}

/**
 * 首页金额汇总。汇率只使用记录上已保存且目标币种匹配的快照；不会请求网络或猜测汇率。
 * 累计金额不需要日期；年度金额按购买日期（缺失时由调用方传入批次日期）归年。
 */
object HomeSpendingEngine {

    fun summarize(records: List<BudgetRecordInput>, targetCurrency: String, year: Int): HomeSpendingSummary {
        val target = Money.normalize(targetCurrency)
        var allTime = 0L
        var allTimeCount = 0
        var allTimeUnconverted = 0
        var currentYear = 0L
        var currentYearCount = 0
        var currentYearUnconverted = 0
        var currentYearUnpriced = 0

        records.forEach { record ->
            val inCurrentYear = record.date?.year == year
            val amount = record.amountMinor
            if (amount == null) {
                if (inCurrentYear && record.kind == BudgetRecordKind.COPY) currentYearUnpriced++
                return@forEach
            }

            allTimeCount++
            if (inCurrentYear) currentYearCount++

            val converted = when {
                Money.normalize(record.currency) == target -> amount
                record.fxRateScaled != null && record.fxToCurrency?.let(Money::normalize) == target ->
                    Fx.convert(amount, record.currency, record.fxRateScaled, target)
                else -> null
            }

            if (converted == null) {
                allTimeUnconverted++
                if (inCurrentYear) currentYearUnconverted++
            } else {
                allTime = Money.add(allTime, converted)
                if (inCurrentYear) currentYear = Money.add(currentYear, converted)
            }
        }

        return HomeSpendingSummary(
            currency = target,
            year = year,
            allTimeMinor = allTime,
            allTimeRecordCount = allTimeCount,
            allTimeUnconvertedCount = allTimeUnconverted,
            currentYearMinor = currentYear,
            currentYearRecordCount = currentYearCount,
            currentYearUnconvertedCount = currentYearUnconverted,
            currentYearUnpricedCount = currentYearUnpriced,
        )
    }
}
