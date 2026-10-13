package com.vnventory.app.domain.cost

import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.Fx
import com.vnventory.app.domain.model.Money
import com.vnventory.app.domain.model.OwnedCopy

/** 首页消费分类合计；统一币种模式无法按定格汇率折算的条目不计入，并明确返回数量。 */
data class SpendingStatsSummary(
    val goods: Map<String, Long>,
    val shipping: Map<String, Long>,
    val fees: Map<String, Long>,
    val taxes: Map<String, Long>,
    val other: Map<String, Long>,
    val unconvertedCount: Int,
)

/**
 * 已记录消费汇总。
 *
 * 不读取实时汇率：统一币种时只使用原币金额或目标币种匹配的既有定格快照；缺少匹配快照的记录
 * 不会被猜测或静默计入，而由 [SpendingStatsSummary.unconvertedCount] 交给界面提示。
 */
object SpendingStatsEngine {
    private enum class Bucket { GOODS, SHIPPING, FEES, TAXES, OTHER }

    fun summarize(
        copies: List<OwnedCopy>,
        expenses: List<Expense>,
        targetCurrency: String? = null,
    ): SpendingStatsSummary {
        val target = targetCurrency?.let(Money::normalize)
        val values = mutableMapOf<Bucket, MutableList<Pair<String, Long>>>()
        var unconvertedCount = 0

        fun add(
            bucket: Bucket,
            amount: Long,
            currency: String,
            fxRateScaled: Long?,
            fxToCurrency: String?,
        ) {
            val source = Money.normalize(currency)
            val (outputCurrency, outputAmount) = when {
                target == null -> source to amount
                source == target -> target to amount
                fxRateScaled != null && fxToCurrency?.let(Money::normalize) == target ->
                    target to Fx.convert(amount, source, fxRateScaled, target)
                else -> {
                    unconvertedCount++
                    return
                }
            }
            values.getOrPut(bucket) { mutableListOf() }.add(outputCurrency to outputAmount)
        }

        copies.forEach { copy ->
            copy.priceMinor?.let { amount ->
                add(Bucket.GOODS, amount, copy.currency, copy.fxRateScaled, copy.fxToCurrency)
            }
        }
        expenses.forEach { expense ->
            val bucket = when (expense.category) {
                ExpenseCategory.INTERNATIONAL_SHIPPING,
                ExpenseCategory.ISLAND_SHIPPING,
                ExpenseCategory.DOMESTIC_SHIPPING,
                ExpenseCategory.SHIPPING -> Bucket.SHIPPING
                ExpenseCategory.PAYMENT_FEE, ExpenseCategory.FEE -> Bucket.FEES
                ExpenseCategory.TAX -> Bucket.TAXES
                ExpenseCategory.OTHER -> Bucket.OTHER
            }
            add(bucket, expense.amountMinor, expense.currency, expense.fxRateScaled, expense.fxToCurrency)
        }

        fun totals(bucket: Bucket): Map<String, Long> = Money.totals(values[bucket].orEmpty())
        return SpendingStatsSummary(
            goods = totals(Bucket.GOODS),
            shipping = totals(Bucket.SHIPPING),
            fees = totals(Bucket.FEES),
            taxes = totals(Bucket.TAXES),
            other = totals(Bucket.OTHER),
            unconvertedCount = unconvertedCount,
        )
    }
}
