package com.vnventory.app.domain.cost

import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.Money
import java.math.BigInteger
import com.vnventory.app.domain.text.Message
import com.vnventory.app.domain.text.MessageKey
import com.vnventory.app.domain.text.message
import com.vnventory.app.domain.text.requireMessage

data class CostCopyInput(val copyId: Long, val priceMinor: Long, val currency: String)

data class CostExpenseInput(
    val expenseId: Long,
    val name: String,
    val category: ExpenseCategory,
    val amountMinor: Long,
    val currency: String,
    val mode: AllocationMode,
    val manualAllocations: Map<Long, Long> = emptyMap(),
)

enum class FeeKind { EQUAL_POOL, BY_PRICE_POOL, MANUAL }

data class FeeShare(
    val expenseId: Long?,
    val label: Message,
    val kind: FeeKind,
    val amountMinor: Long,
    val currency: String,
)

data class CopyCost(
    val copyId: Long,
    val basePriceMinor: Long,
    val baseCurrency: String,
    val feeShares: List<FeeShare>,
) {
    val totalsByCurrency: Map<String, Long> = Money.totals(
        listOf(baseCurrency to basePriceMinor) + feeShares.map { it.currency to it.amountMinor }
    )
}

data class OrderCostBreakdown(
    val copyCosts: List<CopyCost>,
    /** 订单实际支出：商品 + 全部费用，与订单列表/首页保持同口径。 */
    val totalsByCurrency: Map<String, Long>,
    val goodsTotals: Map<String, Long> = emptyMap(),
    val feeTotals: Map<String, Long> = emptyMap(),
    val allocatedTotals: Map<String, Long> = emptyMap(),
    val unallocatedTotals: Map<String, Long> = emptyMap(),
    /** 旧数据中的无效分摊不静默猜测：暂不分摊并提示修正。 */
    val issues: List<Message> = emptyList(),
)

/** 纯函数成本引擎。自动费用按方式+币种池化，最大余数法确保每池总额守恒。 */
object CostEngine {
    fun expenseProblem(expense: CostExpenseInput, copies: List<CostCopyInput>): Message? {
        if (expense.amountMinor < 0) return message(MessageKey.EXPENSE_NEGATIVE)
        if (expense.mode == AllocationMode.BY_PRICE && copies.map { Money.normalize(it.currency) }.distinct().size > 1) {
            return message(MessageKey.MIXED_CURRENCY_ALLOCATION)
        }
        if (expense.mode == AllocationMode.MANUAL) {
            val ids = copies.map { it.copyId }.toSet()
            if (expense.manualAllocations.keys.any { it !in ids }) return message(MessageKey.ALLOCATION_COPY_MISMATCH)
            if (expense.manualAllocations.values.any { it < 0 }) return message(MessageKey.ALLOCATION_NEGATIVE)
            val total = try { Money.sum(expense.manualAllocations.values) } catch (_: IllegalArgumentException) {
                return message(MessageKey.ALLOCATION_TOTAL_OVERFLOW)
            }
            if (total > expense.amountMinor) return message(MessageKey.ALLOCATION_EXCESS)
        }
        return null
    }

    fun computeOrderCosts(copies: List<CostCopyInput>, expenses: List<CostExpenseInput>): OrderCostBreakdown {
        requireMessage(copies.map { it.copyId }.distinct().size == copies.size) { message(MessageKey.COPY_IDS_DUPLICATE) }
        requireMessage(copies.all { it.priceMinor >= 0 }) { message(MessageKey.COPY_PRICE_NEGATIVE) }
        requireMessage(expenses.all { it.amountMinor >= 0 }) { message(MessageKey.EXPENSE_NEGATIVE) }
        val shares = copies.associate { it.copyId to mutableListOf<FeeShare>() }
        val issues = mutableListOf<Message>()
        val valid = expenses.filter { expense ->
            val problem = expenseProblem(expense, copies)
            if (problem != null) issues.add(message(MessageKey.ALLOCATION_ISSUE, expense.name, problem))
            problem == null
        }
        valid.filter { it.mode != AllocationMode.MANUAL }
            .groupBy { it.mode to Money.normalize(it.currency) }
            .forEach { (key, group) ->
                val (mode, currency) = key
                val amount = Money.sum(group.map { it.amountMinor })
                val weights = if (mode == AllocationMode.EQUAL) copies.map { 1L } else copies.map { it.priceMinor }
                val split = allocateProportionally(amount, weights)
                copies.forEachIndexed { index, copy ->
                    shares.getValue(copy.copyId).add(FeeShare(
                        null, if (group.size == 1) Message.Literal(group.single().name) else message(MessageKey.ALLOCATION_POOL, mode.label, group.size),
                        if (mode == AllocationMode.EQUAL) FeeKind.EQUAL_POOL else FeeKind.BY_PRICE_POOL,
                        split[index], currency,
                    ))
                }
            }
        valid.filter { it.mode == AllocationMode.MANUAL }.forEach { expense ->
            expense.manualAllocations.forEach { (id, amount) ->
                shares.getValue(id).add(FeeShare(expense.expenseId, Message.Literal(expense.name), FeeKind.MANUAL, amount, expense.currency))
            }
        }
        val costs = copies.map { CopyCost(it.copyId, it.priceMinor, it.currency, shares.getValue(it.copyId)) }
        val goods = Money.totals(copies.map { it.currency to it.priceMinor })
        val fees = Money.totals(expenses.map { it.currency to it.amountMinor })
        val assignedFees = Money.totals(shares.values.flatten().map { it.currency to it.amountMinor })
        val unallocated = fees.mapValues { (currency, amount) -> amount - (assignedFees[currency] ?: 0L) }
            .filterValues { it != 0L }
        return OrderCostBreakdown(
            copyCosts = costs,
            totalsByCurrency = Money.totals(goods.toList() + fees.toList()),
            goodsTotals = goods,
            feeTotals = fees,
            allocatedTotals = Money.totals(costs.flatMap { it.totalsByCurrency.toList() }),
            unallocatedTotals = unallocated,
            issues = issues,
        )
    }

    /** 单笔分配仅用于预填手动输入。编辑预览必须使用完整订单的 computeOrderCosts。 */
    fun allocateExpense(expense: CostExpenseInput, copies: List<CostCopyInput>): Map<Long, Long> {
        val problem = expenseProblem(expense, copies)
        requireMessage(problem == null) { problem!! }
        if (expense.mode == AllocationMode.MANUAL) return expense.manualAllocations
        val weights = if (expense.mode == AllocationMode.EQUAL) copies.map { 1L } else copies.map { it.priceMinor }
        return copies.map { it.copyId }.zip(allocateProportionally(expense.amountMinor, weights)).toMap()
    }

    fun equalSplitPreview(totalMinor: Long, copyCount: Int): List<Long> =
        allocateProportionally(totalMinor, List(copyCount.coerceAtLeast(0)) { 1L })

    internal fun allocateProportionally(totalMinor: Long, weights: List<Long>): List<Long> {
        requireMessage(totalMinor >= 0) { message(MessageKey.AMOUNT_NEGATIVE) }
        requireMessage(weights.all { it >= 0 }) { message(MessageKey.WEIGHT_NEGATIVE) }
        if (weights.isEmpty()) return emptyList()
        val effective = if (weights.all { it == 0L }) weights.map { 1L } else weights
        val sum = effective.fold(BigInteger.ZERO) { acc, w -> acc + BigInteger.valueOf(w) }
        val quotients = effective.map { BigInteger.valueOf(totalMinor).multiply(BigInteger.valueOf(it)).divideAndRemainder(sum) }
        val floors = quotients.map { it[0].longValueExact() }.toMutableList()
        val remainder = totalMinor - Money.sum(floors)
        val order = weights.indices.sortedWith(compareByDescending<Int> { quotients[it][1] }.thenBy { it })
        repeat(remainder.toInt()) { floors[order[it]] += 1L }
        return floors
    }
}
