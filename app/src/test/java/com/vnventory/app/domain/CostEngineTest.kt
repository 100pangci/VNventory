package com.vnventory.app.domain

import com.vnventory.app.domain.cost.CostCopyInput
import com.vnventory.app.domain.cost.CostEngine
import com.vnventory.app.domain.cost.CostExpenseInput
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CostEngineTest {

    private fun copy(id: Long, price: Long, currency: String = "JPY") =
        CostCopyInput(copyId = id, priceMinor = price, currency = currency)

    private fun expense(
        id: Long,
        amount: Long,
        mode: AllocationMode,
        currency: String = "JPY",
        manual: Map<Long, Long> = emptyMap(),
    ) = CostExpenseInput(
        expenseId = id,
        name = "费用$id",
        category = ExpenseCategory.OTHER,
        amountMinor = amount,
        currency = currency,
        mode = mode,
        manualAllocations = manual,
    )

    // ---- 用户给出的示例 ----

    @Test
    fun `示例 均摊后最终成本 A84 B94 C104`() {
        val copies = listOf(copy(1, 50), copy(2, 60), copy(3, 70))
        val expenses = listOf(
            expense(101, 2, AllocationMode.EQUAL),   // 国际支付手续费
            expense(102, 100, AllocationMode.EQUAL), // 国际运费
        )
        val breakdown = CostEngine.computeOrderCosts(copies, expenses)

        // 每盒附加费用 = 34
        breakdown.copyCosts.forEach { cc ->
            assertEquals("copy ${cc.copyId}", 34L, cc.feeShares.sumOf { it.amountMinor })
        }
        // A=84 B=94 C=104
        assertEquals(84L, breakdown.copyCosts[0].totalsByCurrency["JPY"])
        assertEquals(94L, breakdown.copyCosts[1].totalsByCurrency["JPY"])
        assertEquals(104L, breakdown.copyCosts[2].totalsByCurrency["JPY"])
        assertEquals(282L, breakdown.totalsByCurrency["JPY"])
    }

    // ---- 池化 ----

    @Test
    fun `同模式同币种费用池化后再分摊`() {
        val copies = listOf(copy(1, 0), copy(2, 0))
        val expenses = listOf(
            expense(1, 1, AllocationMode.EQUAL),
            expense(2, 1, AllocationMode.EQUAL),
        )
        val breakdown = CostEngine.computeOrderCosts(copies, expenses)
        // 逐笔分摊会得到 [2,0]；池化后 2 円平均分给 2 盒 = 每盒 1
        assertEquals(1L, breakdown.copyCosts[0].feeShares.sumOf { it.amountMinor })
        assertEquals(1L, breakdown.copyCosts[1].feeShares.sumOf { it.amountMinor })
    }

    @Test
    fun `池化条目的标签与类型`() {
        val copies = listOf(copy(1, 0), copy(2, 0), copy(3, 0))
        val expenses = listOf(
            expense(1, 2, AllocationMode.EQUAL),
            expense(2, 100, AllocationMode.EQUAL),
        )
        val breakdown = CostEngine.computeOrderCosts(copies, expenses)
        val share = breakdown.copyCosts[0].feeShares.single()
        assertEquals("平均分摊（2 笔）", share.label)
        assertEquals(com.vnventory.app.domain.cost.FeeKind.EQUAL_POOL, share.kind)
        assertNull(share.expenseId)
    }

    @Test
    fun `手动分摊按费用逐笔归属并计入最终成本`() {
        val copies = listOf(copy(1, 50), copy(2, 60))
        val expenses = listOf(
            expense(9, 100, AllocationMode.MANUAL, manual = mapOf(1L to 100L)),
        )
        val breakdown = CostEngine.computeOrderCosts(copies, expenses)
        assertEquals(150L, breakdown.copyCosts[0].totalsByCurrency["JPY"])
        assertEquals(60L, breakdown.copyCosts[1].totalsByCurrency["JPY"])
        assertEquals(210L, breakdown.totalsByCurrency["JPY"])
        val share = breakdown.copyCosts[0].feeShares.single()
        assertEquals(com.vnventory.app.domain.cost.FeeKind.MANUAL, share.kind)
        assertEquals(9L, share.expenseId)
    }

    // ---- 均摊 ----

    @Test
    fun `均摊除不尽时余数按最大余数法分配且总和不变`() {
        val copies = listOf(copy(1, 0), copy(2, 0), copy(3, 0))
        val allocations = CostEngine.allocateExpense(expense(1, 100, AllocationMode.EQUAL), copies)
        assertEquals(listOf(34L, 33L, 33L), listOf(allocations[1L], allocations[2L], allocations[3L]))
        assertEquals(100L, allocations.values.sum())
    }

    @Test
    fun `均摊 2 日元到三盒`() {
        val copies = listOf(copy(1, 0), copy(2, 0), copy(3, 0))
        val allocations = CostEngine.allocateExpense(expense(1, 2, AllocationMode.EQUAL), copies)
        assertEquals(listOf(1L, 1L, 0L), listOf(allocations[1L], allocations[2L], allocations[3L]))
        assertEquals(2L, allocations.values.sum())
    }

    @Test
    fun `均摊 1 日元到两盒取索引小者`() {
        val copies = listOf(copy(1, 0), copy(2, 0))
        val allocations = CostEngine.allocateExpense(expense(1, 1, AllocationMode.EQUAL), copies)
        assertEquals(1L, allocations[1L])
        assertEquals(0L, allocations[2L])
    }

    // ---- 按价格比例 ----

    @Test
    fun `按价格比例分摊`() {
        val copies = listOf(copy(1, 50), copy(2, 60), copy(3, 70))
        val allocations = CostEngine.allocateExpense(expense(1, 100, AllocationMode.BY_PRICE), copies)
        // 28 + 33 + 39 = 100
        assertEquals(listOf(28L, 33L, 39L), listOf(allocations[1L], allocations[2L], allocations[3L]))
        assertEquals(100L, allocations.values.sum())
    }

    @Test
    fun `按价格比例 全部零价时退化为均摊`() {
        val copies = listOf(copy(1, 0), copy(2, 0), copy(3, 0))
        val allocations = CostEngine.allocateExpense(expense(1, 100, AllocationMode.BY_PRICE), copies)
        assertEquals(100L, allocations.values.sum())
    }

    @Test
    fun `免费商品按价格比例不承担费用`() {
        val copies = listOf(copy(1, 100), copy(2, 0))
        val allocations = CostEngine.allocateExpense(expense(1, 50, AllocationMode.BY_PRICE), copies)
        assertEquals(50L, allocations[1L])
        assertEquals(0L, allocations[2L])
    }

    // ---- 手动指定 ----

    @Test
    fun `手动指定只认属于该订单的盒子`() {
        val copies = listOf(copy(1, 50), copy(2, 60))
        assertThrows(IllegalArgumentException::class.java) {
            CostEngine.allocateExpense(expense(1, 30, AllocationMode.MANUAL, manual = mapOf(1L to 10L, 99L to 20L)), copies)
        }
    }

    // ---- 多币种 ----

    @Test
    fun `多币种不做隐式换算 分行汇总`() {
        val copies = listOf(copy(1, 5000), copy(2, 5000))
        val expenses = listOf(
            expense(101, 100, AllocationMode.EQUAL, currency = "JPY"),
            expense(102, 30, AllocationMode.EQUAL, currency = "CNY"),
        )
        val breakdown = CostEngine.computeOrderCosts(copies, expenses)

        // 每盒： JPY 5050 + CNY 15
        breakdown.copyCosts.forEach { cc ->
            assertEquals(5050L, cc.totalsByCurrency["JPY"])
            assertEquals(15L, cc.totalsByCurrency["CNY"])
        }
        assertEquals(10100L, breakdown.totalsByCurrency["JPY"])
        assertEquals(30L, breakdown.totalsByCurrency["CNY"])
    }

    // ---- 边界 ----

    @Test
    fun `单盒订单全部费用归到该盒`() {
        val copies = listOf(copy(1, 5000))
        val expenses = listOf(
            expense(101, 1200, AllocationMode.EQUAL),
            expense(102, 300, AllocationMode.BY_PRICE),
            expense(103, 55, AllocationMode.MANUAL, manual = mapOf(1L to 55L)),
        )
        val breakdown = CostEngine.computeOrderCosts(copies, expenses)
        assertEquals(6555L, breakdown.copyCosts[0].totalsByCurrency["JPY"])
    }

    @Test
    fun `空订单不崩溃`() {
        val breakdown = CostEngine.computeOrderCosts(emptyList(), listOf(expense(1, 100, AllocationMode.EQUAL)))
        assertEquals(emptyList<Long>(), breakdown.copyCosts.map { it.copyId })
        assertEquals(mapOf("JPY" to 100L), breakdown.totalsByCurrency)
        assertEquals(mapOf("JPY" to 100L), breakdown.unallocatedTotals)
    }

    @Test
    fun `零金额费用全为零`() {
        val copies = listOf(copy(1, 10), copy(2, 20))
        val allocations = CostEngine.allocateExpense(expense(1, 0, AllocationMode.EQUAL), copies)
        assertEquals(0L, allocations.values.sum())
    }

    @Test
    fun `负数金额直接拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            CostEngine.allocateProportionally(-1, listOf(1L))
        }
    }

    @Test
    fun `大量分摊不溢出`() {
        // Long.MAX_VALUE/4 级别的金额与价格
        val big = Long.MAX_VALUE / 4
        val copies = listOf(copy(1, big), copy(2, big), copy(3, big))
        val allocations = CostEngine.allocateExpense(expense(1, big, AllocationMode.BY_PRICE), copies)
        assertEquals(big, allocations.values.sum())
    }

    @Test fun `混币种商品拒绝比例分摊且历史数据不伪造汇率`() {
        val copies = listOf(copy(1, 10000, "JPY"), copy(2, 10000, "CNY"))
        val fee = expense(1, 100, AllocationMode.BY_PRICE)
        assertThrows(IllegalArgumentException::class.java) { CostEngine.allocateExpense(fee, copies) }
        val result = CostEngine.computeOrderCosts(copies, listOf(fee))
        assertEquals(mapOf("JPY" to 100L), result.unallocatedTotals)
        assertEquals(1, result.issues.size)
    }

    @Test fun `费用币种与商品不同仍可用同币种商品价格比例`() {
        val result = CostEngine.allocateExpense(expense(1, 90, AllocationMode.BY_PRICE, "CNY"), listOf(copy(1, 100), copy(2, 200)))
        assertEquals(mapOf(1L to 30L, 2L to 60L), result)
    }

    @Test fun `手动分摊不可负数超额或溢出`() {
        for (manual in listOf(mapOf(1L to -1L), mapOf(1L to 101L), mapOf(1L to Long.MAX_VALUE, 2L to Long.MAX_VALUE))) {
            assertThrows(IllegalArgumentException::class.java) {
                CostEngine.allocateExpense(expense(1, 100, AllocationMode.MANUAL, manual = manual), listOf(copy(1, 50), copy(2, 60)))
            }
        }
    }

    @Test fun `部分分摊保证支出等于收藏成本加未分摊`() {
        val result = CostEngine.computeOrderCosts(listOf(copy(1, 100)), listOf(expense(1, 100, AllocationMode.MANUAL, manual = mapOf(1L to 40L))))
        assertEquals(mapOf("JPY" to 200L), result.totalsByCurrency)
        assertEquals(mapOf("JPY" to 140L), result.allocatedTotals)
        assertEquals(mapOf("JPY" to 60L), result.unallocatedTotals)
    }

    @Test fun `费用池与最终成本都检测溢出`() {
        assertThrows(IllegalArgumentException::class.java) {
            CostEngine.computeOrderCosts(listOf(copy(1, 0)), listOf(expense(1, Long.MAX_VALUE, AllocationMode.EQUAL), expense(2, 1, AllocationMode.EQUAL)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CostEngine.computeOrderCosts(listOf(copy(1, Long.MAX_VALUE)), listOf(expense(1, 1, AllocationMode.EQUAL)))
        }
    }
}
