package com.vnventory.app.domain

import com.vnventory.app.domain.cost.BudgetEngine
import com.vnventory.app.domain.cost.BudgetIssueReason
import com.vnventory.app.domain.cost.BudgetRecordInput
import com.vnventory.app.domain.cost.BudgetRecordKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetEngineTest {

    private fun copy(
        id: Long,
        amount: Long?,
        currency: String = "CNY",
        date: java.time.LocalDate? = java.time.LocalDate.of(2026, 1, 10),
        fx: Long? = null,
        to: String? = null,
    ) = BudgetRecordInput(BudgetRecordKind.COPY, id, amount, currency, date, null, fx, to)

    private fun expense(
        id: Long,
        amount: Long,
        currency: String = "CNY",
        date: java.time.LocalDate? = java.time.LocalDate.of(2026, 1, 10),
        fx: Long? = null,
        to: String? = null,
    ) = BudgetRecordInput(BudgetRecordKind.EXPENSE, id, amount, currency, date, 1L, fx, to)

    @Test fun `同币种直接按年计入`() {
        val summary = BudgetEngine.summarize(listOf(copy(1, 1_000), copy(2, 2_000), copy(3, 500, date = java.time.LocalDate.of(2025, 6, 1))), "CNY")
        assertEquals(3_000L, summary.totalForYear(2026))
        assertEquals(500L, summary.totalForYear(2025))
        assertEquals(0L, summary.totalForYear(2024))
        assertEquals(0, summary.issues.size)
    }

    @Test fun `外币按定格汇率折算`() {
        // 12,800 日元 × 0.042299852 ≈ ¥541.44
        val summary = BudgetEngine.summarize(
            listOf(copy(1, 12_800, currency = "JPY", fx = 42_299_852L, to = "CNY")),
            "CNY",
        )
        assertEquals(54_144L, summary.totalForYear(2026))
        assertEquals(0, summary.issues.size)
    }

    @Test fun `未记录价格与缺日期分类且不计入`() {
        val summary = BudgetEngine.summarize(
            listOf(copy(1, null), copy(2, 1_000, date = null), copy(3, 1_000)),
            "CNY",
        )
        assertEquals(1_000L, summary.totalForYear(2026))
        val byReason = summary.issuesByReason
        assertEquals(1, byReason[BudgetIssueReason.NO_PRICE]!!.size)
        assertEquals(1, byReason[BudgetIssueReason.NO_DATE]!!.size)
        assertEquals(1L, byReason[BudgetIssueReason.NO_PRICE]!!.single().id)
        assertEquals(2L, byReason[BudgetIssueReason.NO_DATE]!!.single().id)
    }

    @Test fun `外币未定格不计入`() {
        val summary = BudgetEngine.summarize(listOf(copy(1, 12_800, currency = "JPY")), "CNY")
        assertNull(summary.totalsByYear[2026])
        assertEquals(1, summary.issuesByReason[BudgetIssueReason.NOT_FROZEN]!!.size)
    }

    @Test fun `定格币种与当前目标不符不计入`() {
        val summary = BudgetEngine.summarize(
            listOf(copy(1, 12_800, currency = "JPY", fx = 42_299_852L, to = "USD")),
            "CNY",
        )
        assertNull(summary.totalsByYear[2026])
        assertEquals(1, summary.issuesByReason[BudgetIssueReason.STALE_TARGET]!!.size)
    }

    @Test fun `费用计入且按币种折算`() {
        val summary = BudgetEngine.summarize(
            listOf(
                copy(1, 12_800, currency = "JPY", fx = 42_299_852L, to = "CNY"),
                expense(7, 500, currency = "JPY", fx = 42_299_852L, to = "CNY"),
            ),
            "CNY",
        )
        // 12,800 + 500 = 13,300 日元 × 0.042299852 ≈ ¥562.59
        assertEquals(56_259L, summary.totalForYear(2026))
    }

    @Test fun `混合口径下的问题记录带来源与订单`() {
        val summary = BudgetEngine.summarize(
            listOf(
                copy(1, null),
                expense(2, 100, currency = "JPY", date = null),
            ),
            "CNY",
        )
        assertEquals(1L, summary.issuesByReason[BudgetIssueReason.NO_PRICE]!!.single().id)
        val expenseIssue = summary.issuesByReason[BudgetIssueReason.NO_DATE]!!.single()
        assertEquals(BudgetRecordKind.EXPENSE, expenseIssue.kind)
        assertEquals(1L, expenseIssue.orderId)
    }

    @Test fun `多币种同一年份分别折算汇总`() {
        val summary = BudgetEngine.summarize(
            listOf(
                copy(1, 1_000, currency = "CNY"),
                copy(2, 25_000, currency = "JPY", fx = 50_000_000L, to = "CNY"),
                copy(3, 100, currency = "JPY", date = java.time.LocalDate.of(2025, 6, 1), fx = 50_000_000L, to = "CNY"),
            ),
            "CNY",
        )
        // 1,000 分 + 25,000×0.05=1,250 元(125,000 分) -> 126,000 分
        assertEquals(126_000L, summary.totalForYear(2026))
        assertEquals(500L, summary.totalForYear(2025))
    }
}
