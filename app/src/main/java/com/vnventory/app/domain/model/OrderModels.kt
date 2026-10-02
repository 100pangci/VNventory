package com.vnventory.app.domain.model

import com.vnventory.app.domain.cost.CopyCost
import com.vnventory.app.domain.cost.OrderCostBreakdown

/** 订单列表项（含统计 = 本体价 + 费用，均按币种分组） */
data class OrderSummary(
    val order: PurchaseOrder,
    val copyCount: Int,
    val goodsTotals: Map<String, Long>,
    val feeTotals: Map<String, Long>,
) {
    /** 本体价 + 费用 */
    val grandTotals: Map<String, Long> = mergeTotals(goodsTotals, feeTotals)
}

/** 订单详情（含每盒实时成本分摊） */
data class OrderDetail(
    val order: PurchaseOrder,
    val copies: List<OwnedCopy>,
    val expenses: List<Expense>,
    val breakdown: OrderCostBreakdown,
) {
    fun costFor(copyId: Long): CopyCost? = breakdown.copyCosts.firstOrNull { it.copyId == copyId }
}

internal fun mergeTotals(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> {
    val out = a.toMutableMap()
    b.forEach { (currency, amount) -> out[currency] = (out[currency] ?: 0L) + amount }
    return out
}
