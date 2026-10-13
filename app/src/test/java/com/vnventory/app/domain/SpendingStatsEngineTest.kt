package com.vnventory.app.domain

import com.vnventory.app.domain.cost.SpendingStatsEngine
import com.vnventory.app.domain.model.AllocationMode
import com.vnventory.app.domain.model.CopyCondition
import com.vnventory.app.domain.model.Expense
import com.vnventory.app.domain.model.ExpenseCategory
import com.vnventory.app.domain.model.OwnedCopy
import org.junit.Assert.assertEquals
import org.junit.Test

class SpendingStatsEngineTest {
    private fun copy(
        id: Long,
        amount: Long,
        currency: String,
        rate: Long? = null,
        rateTo: String? = null,
    ) = OwnedCopy(
        id = id,
        vnId = "v$id",
        releaseId = null,
        vnTitle = "VN $id",
        releaseTitle = null,
        coverUrl = null,
        priceMinor = amount,
        currency = currency,
        condition = CopyCondition.NEW,
        conditionNote = null,
        purchaseDate = null,
        shop = null,
        orderId = null,
        notes = null,
        createdAt = id,
        updatedAt = id,
        fxRateScaled = rate,
        fxToCurrency = rateTo,
    )

    private fun expense(
        id: Long,
        category: ExpenseCategory,
        amount: Long,
        currency: String,
        rate: Long? = null,
        rateTo: String? = null,
    ) = Expense(
        id = id,
        orderId = 1,
        name = category.name,
        category = category,
        amountMinor = amount,
        currency = currency,
        mode = AllocationMode.EQUAL,
        notes = null,
        createdAt = id,
        fxRateScaled = rate,
        fxToCurrency = rateTo,
    )

    @Test fun `默认按各笔原币种分组且不换算`() {
        val result = SpendingStatsEngine.summarize(
            copies = listOf(copy(1, 100, "JPY", 50_000_000, "CNY"), copy(2, 230, "CNY")),
            expenses = listOf(expense(3, ExpenseCategory.SHIPPING, 40, "JPY")),
        )

        assertEquals(mapOf("JPY" to 100L, "CNY" to 230L), result.goods)
        assertEquals(mapOf("JPY" to 40L), result.shipping)
        assertEquals(0, result.unconvertedCount)
    }

    @Test fun `统一币种只折算有匹配定格的记录并报告缺失`() {
        val result = SpendingStatsEngine.summarize(
            copies = listOf(
                copy(1, 100, "JPY", 50_000_000, "CNY"),
                copy(2, 100, "CNY"),
                copy(3, 100, "USD", 50_000_000, "EUR"),
            ),
            expenses = listOf(
                expense(4, ExpenseCategory.SHIPPING, 10, "JPY", 50_000_000, "CNY"),
                expense(5, ExpenseCategory.PAYMENT_FEE, 20, "CNY"),
                expense(6, ExpenseCategory.TAX, 30, "USD"),
                expense(7, ExpenseCategory.OTHER, 40, "CNY"),
            ),
            targetCurrency = "cny",
        )

        assertEquals(mapOf("CNY" to 600L), result.goods)
        assertEquals(mapOf("CNY" to 50L), result.shipping)
        assertEquals(mapOf("CNY" to 20L), result.fees)
        assertEquals(emptyMap<String, Long>(), result.taxes)
        assertEquals(mapOf("CNY" to 40L), result.other)
        assertEquals(2, result.unconvertedCount)
    }
}
