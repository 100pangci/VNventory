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
)
