package com.vnventory.app.domain.model

/**
 * 通用费用条目。
 *
 * 分摊规则（见 CostEngine）：
 * - EQUAL / BY_PRICE 不落库分摊结果，实时计算，避免保存易失真的结果；
 * - MANUAL 时 [allocations] 保存用户手动指定到各盒的金额。
 */
data class Expense(
    val id: Long,
    val orderId: Long,
    val name: String,
    val category: ExpenseCategory,
    val amountMinor: Long,
    val currency: String,
    val mode: AllocationMode,
    val notes: String?,
    val createdAt: Long,
    /** 手动分摊明细：copyId -> 金额（仅 MANUAL 模式有值） */
    val allocations: Map<Long, Long> = emptyMap(),
    /** 定格汇率 ×10^9（1 单位本币 = rate/1e9 单位目标币）；null = 未定格 */
    val fxRateScaled: Long? = null,
    /** 实际采用的汇率日期 */
    val fxDate: java.time.LocalDate? = null,
    /** 定格时的目标币种 */
    val fxToCurrency: String? = null,
    /** 数据源（currency-api / frankfurter / manual） */
    val fxSource: String? = null,
) {
    val displayName get() = if (category.isFixed && name == category.name) category.label
        else com.vnventory.app.domain.text.Message.Literal(name)
}
