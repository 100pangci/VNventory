package com.vnventory.app.domain.cost

import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.ExpenseCategory
import java.math.BigInteger

// ---------------------------------------------------------------------------
// 输入（由仓库层从实体投影而来，尽量最小化，便于单测）
// ---------------------------------------------------------------------------

data class CostCopyInput(
    val copyId: Long,
    val priceMinor: Long,
    val currency: String,
)

data class CostExpenseInput(
    val expenseId: Long,
    val name: String,
    val category: ExpenseCategory,
    val amountMinor: Long,
    val currency: String,
    val mode: AllocationMode,
    /** 仅 MANUAL 模式使用：copyId -> 金额（最小单位） */
    val manualAllocations: Map<Long, Long> = emptyMap(),
)

// ---------------------------------------------------------------------------
// 输出
// ---------------------------------------------------------------------------

enum class FeeKind { EQUAL_POOL, BY_PRICE_POOL, MANUAL }

/**
 * 一盒分摊到的一笔费用。
 *
 * EQUAL / BY_PRICE 采用「同模式 + 同币种先汇总成池再分摊」的策略
 * （见 [CostEngine.computeOrderCosts]），此时 [expenseId] 为 null、
 * [label] 为池标签；MANUAL 保持逐笔归属，[label] 为费用名。
 */
data class FeeShare(
    val expenseId: Long?,
    val label: String,
    val kind: FeeKind,
    val amountMinor: Long,
    val currency: String,
)

/** 一盒的最终成本：本体价 + 各费用分摊，按币种分列 */
data class CopyCost(
    val copyId: Long,
    val basePriceMinor: Long,
    val baseCurrency: String,
    val feeShares: List<FeeShare>,
) {
    /** 该盒最终成本（按币种） */
    val totalsByCurrency: Map<String, Long> = buildMap {
        put(baseCurrency, (get(baseCurrency) ?: 0L) + basePriceMinor)
        feeShares.forEach { share ->
            put(share.currency, (get(share.currency) ?: 0L) + share.amountMinor)
        }
    }
}

/** 整个订单的成本汇总（按币种） */
data class OrderCostBreakdown(
    val copyCosts: List<CopyCost>,
    val totalsByCurrency: Map<String, Long>,
)

// ---------------------------------------------------------------------------
// 引擎
// ---------------------------------------------------------------------------

/**
 * 成本分摊引擎（纯函数，无副作用）。
 *
 * 设计要点：
 * - **池化分摊**：EQUAL（平均）与 BY_PRICE（按价格比例）的费用按「模式 + 币种」
 *   先汇总成池、再整体分摊到各盒。这样舍入误差最小（每个池最多 1 个单位），
 *   也与直觉一致：例如 2 + 100 平均分摊到 3 盒，每盒附加费 = 102/3 = 34。
 * - **结果不写回数据库**，只用于展示；MANUAL 由用户指定并落库。
 * - **多币种隔离**：不做隐式汇率换算，按币种分行展示。
 * - 所有除法使用「最大余数法」，保证各盒分摊之和 == 该池金额（分毫不差）。
 */
object CostEngine {

    fun computeOrderCosts(
        copies: List<CostCopyInput>,
        expenses: List<CostExpenseInput>,
    ): OrderCostBreakdown {
        val sharesByCopy = copies.associate { it.copyId to mutableListOf<FeeShare>() }

        // 1) 自动模式：按（模式, 币种）池化后分摊
        listOf(AllocationMode.EQUAL, AllocationMode.BY_PRICE).forEach { mode ->
            expenses.filter { it.mode == mode && it.amountMinor > 0 }.groupBy { it.currency }.forEach { (currency, group) ->
                if (copies.isEmpty()) return@forEach
                val total = group.sumOf { it.amountMinor }
                val weights = if (mode == AllocationMode.EQUAL) {
                    List(copies.size) { 1L }
                } else {
                    copies.map { it.priceMinor.coerceAtLeast(0L) }
                }
                val splits = allocateProportionally(total, weights)
                val label = poolLabel(mode, group)
                val kind = if (mode == AllocationMode.EQUAL) FeeKind.EQUAL_POOL else FeeKind.BY_PRICE_POOL
                copies.forEachIndexed { index, copy ->
                    val amount = splits[index]
                    if (amount != 0L) {
                        sharesByCopy[copy.copyId]?.add(FeeShare(null, label, kind, amount, currency))
                    }
                }
            }
        }

        // 2) MANUAL：逐笔归到用户指定的盒
        expenses.filter { it.mode == AllocationMode.MANUAL }.forEach { expense ->
            expense.manualAllocations.forEach { (copyId, amount) ->
                if (amount != 0L && sharesByCopy.containsKey(copyId)) {
                    sharesByCopy[copyId]?.add(
                        FeeShare(
                            expenseId = expense.expenseId,
                            label = expense.name.ifBlank { "手动分摊" },
                            kind = FeeKind.MANUAL,
                            amountMinor = amount,
                            currency = expense.currency,
                        )
                    )
                }
            }
        }

        val copyCosts = copies.map { copy ->
            CopyCost(
                copyId = copy.copyId,
                basePriceMinor = copy.priceMinor,
                baseCurrency = copy.currency,
                feeShares = sharesByCopy[copy.copyId].orEmpty(),
            )
        }

        val totals = mutableMapOf<String, Long>()
        copyCosts.forEach { cost ->
            cost.totalsByCurrency.forEach { (currency, amount) ->
                totals[currency] = (totals[currency] ?: 0L) + amount
            }
        }
        return OrderCostBreakdown(copyCosts = copyCosts, totalsByCurrency = totals)
    }

    /**
     * 单笔费用到各盒的分摊（费用编辑器“预览”用；不影响池化后的正式结果）。
     * 返回：copyId -> 金额（费用币种，最小单位）。
     */
    fun allocateExpense(
        expense: CostExpenseInput,
        copies: List<CostCopyInput>,
    ): Map<Long, Long> {
        if (copies.isEmpty()) return emptyMap()
        return when (expense.mode) {
            AllocationMode.EQUAL ->
                allocateProportionally(expense.amountMinor, List(copies.size) { 1L })
                    .zipCopyWithIds(copies)

            AllocationMode.BY_PRICE -> {
                val weights = copies.map { it.priceMinor.coerceAtLeast(0L) }
                val effective = if (weights.all { it == 0L }) List(copies.size) { 1L } else weights
                allocateProportionally(expense.amountMinor, effective)
                    .zipCopyWithIds(copies)
            }

            AllocationMode.MANUAL ->
                expense.manualAllocations.filterKeys { id -> copies.any { it.copyId == id } }
        }
    }

    /** 均摊预览（费用编辑器切到 MANUAL 时预填用） */
    fun equalSplitPreview(totalMinor: Long, copyCount: Int): List<Long> =
        allocateProportionally(totalMinor, List(copyCount.coerceAtLeast(0)) { 1L })

    private fun poolLabel(mode: AllocationMode, group: List<CostExpenseInput>): String =
        if (group.size == 1) {
            group.first().name.ifBlank { mode.label }
        } else {
            "${mode.label}（${group.size} 笔）"
        }

    private fun List<Long>.zipCopyWithIds(copies: List<CostCopyInput>): Map<Long, Long> =
        copies.mapIndexed { index, copy -> copy.copyId to this[index] }.toMap()

    /**
     * 最大余数法把 [totalMinor] 按 [weights] 比例分给 n 份，保证：
     * - 每份 >= 0；- 各份之和 == totalMinor；- 结果确定（余数相同取索引小者）。
     */
    internal fun allocateProportionally(totalMinor: Long, weights: List<Long>): List<Long> {
        require(totalMinor >= 0) { "金额不能为负数" }
        require(weights.all { it >= 0 }) { "权重不能为负数" }
        val n = weights.size
        if (n == 0) return emptyList()

        val effective = if (weights.all { it == 0L }) List(n) { 1L } else weights
        val weightSum = effective.fold(BigInteger.ZERO) { acc, w -> acc + BigInteger.valueOf(w) }
        val total = BigInteger.valueOf(totalMinor)

        val floors = LongArray(n)
        val remainders = arrayOfNulls<BigInteger>(n)
        var sumFloors = 0L
        for (i in 0 until n) {
            val numerator = total.multiply(BigInteger.valueOf(effective[i]))
            val (q, r) = numerator.divideAndRemainder(weightSum)
            floors[i] = q.toLong()
            remainders[i] = r
            sumFloors += floors[i]
        }

        var remaining = totalMinor - sumFloors
        if (remaining > 0) {
            val order = (0 until n).sortedWith(
                compareByDescending<Int> { remainders[it] }.thenBy { it }
            )
            var pointer = 0
            while (remaining > 0) {
                floors[order[pointer % n]] += 1L
                remaining -= 1L
                pointer += 1
            }
        }
        return floors.toList()
    }
}
