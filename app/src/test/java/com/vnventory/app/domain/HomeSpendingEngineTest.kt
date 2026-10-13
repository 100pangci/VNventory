package com.vnventory.app.domain

import com.vnventory.app.domain.cost.BudgetRecordInput
import com.vnventory.app.domain.cost.BudgetRecordKind
import com.vnventory.app.domain.cost.HomeSpendingEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeSpendingEngineTest {

    @Test fun `主页汇总不依赖预算并按匹配快照折算`() {
        val summary = HomeSpendingEngine.summarize(
            records = listOf(
                // 已定格的本年外币收藏：1000 JPY × 0.05 = 50 CNY。
                BudgetRecordInput(
                    BudgetRecordKind.COPY, 1, 1_000, "JPY", LocalDate.of(2026, 10, 1), 1,
                    50_000_000L, "CNY",
                ),
                // 本币费用直接计入，无须汇率快照。
                BudgetRecordInput(
                    BudgetRecordKind.EXPENSE, 2, 500, "CNY", LocalDate.of(2026, 10, 2), 1,
                    null, null,
                ),
                // 旧记录缺汇率：累计金额不纳入折算值，并单独计数。
                BudgetRecordInput(
                    BudgetRecordKind.COPY, 3, 300, "JPY", LocalDate.of(2025, 10, 1), 2,
                    null, null,
                ),
                // 累计总金额无需日期；年度金额则不能猜测归年。
                BudgetRecordInput(
                    BudgetRecordKind.COPY, 4, 1_200, "CNY", null, null,
                    null, null,
                ),
                // 本年未记录价格不补 0。
                BudgetRecordInput(
                    BudgetRecordKind.COPY, 5, null, "CNY", LocalDate.of(2026, 10, 3), 1,
                    null, null,
                ),
            ),
            targetCurrency = "CNY",
            year = 2026,
        )

        assertEquals("CNY", summary.currency)
        assertEquals(2026, summary.year)
        assertEquals(6_700L, summary.allTimeMinor)
        assertEquals(4, summary.allTimeRecordCount)
        assertEquals(1, summary.allTimeUnconvertedCount)
        assertEquals(5_500L, summary.currentYearMinor)
        assertEquals(2, summary.currentYearRecordCount)
        assertEquals(0, summary.currentYearUnconvertedCount)
        assertEquals(1, summary.currentYearUnpricedCount)
    }

    @Test fun `零金额计入且目标币种不匹配的快照不用于换算`() {
        val summary = HomeSpendingEngine.summarize(
            records = listOf(
                BudgetRecordInput(
                    BudgetRecordKind.COPY, 1, 0, "CNY", LocalDate.of(2026, 1, 1), null,
                    null, null,
                ),
                BudgetRecordInput(
                    BudgetRecordKind.COPY, 2, 1_000, "JPY", LocalDate.of(2026, 1, 1), null,
                    50_000_000L, "USD",
                ),
            ),
            targetCurrency = "CNY",
            year = 2026,
        )

        assertEquals(0L, summary.currentYearMinor)
        assertEquals(2, summary.currentYearRecordCount)
        assertEquals(1, summary.currentYearUnconvertedCount)
        assertEquals(1, summary.allTimeUnconvertedCount)
    }
}
