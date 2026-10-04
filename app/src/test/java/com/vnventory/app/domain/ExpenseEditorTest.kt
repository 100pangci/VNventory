package com.vnventory.app.domain

import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.orders.ExpenseEditorState
import org.junit.Assert.*
import org.junit.Test

class ExpenseEditorTest {
    @Test fun `新费用保存稳定ID历史名称原样保留`() {
        val order = PurchaseOrder(1, "Batch", null, null, "JPY", null, 0, 0)
        val legacy = Expense(2, 1, "历史包装附加费", ExpenseCategory.OTHER, 10, "JPY", AllocationMode.EQUAL, "keep", 12)
        val detail = OrderDetail(order, emptyList(), listOf(legacy), com.vnventory.app.domain.cost.CostEngine.computeOrderCosts(emptyList(), emptyList()))
        val new = ExpenseEditorState(category = ExpenseCategory.DOMESTIC_SHIPPING, amountText = "100", currency = "JPY").candidate(detail)
        assertEquals("DOMESTIC_SHIPPING", new.name)
        assertEquals(ExpenseCategory.DOMESTIC_SHIPPING.label, new.displayName)
        val edited = ExpenseEditorState(editingId = 2, name = legacy.name, category = legacy.category, amountText = "20", currency = "JPY").candidate(detail)
        assertEquals(legacy.name, edited.name)
        assertEquals(legacy.notes, edited.notes)
        assertEquals(legacy.createdAt, edited.createdAt)
    }
    @Test fun `新建费用只有五种固定类别而历史类别可继续编辑`() {
        assertEquals(listOf(ExpenseCategory.INTERNATIONAL_SHIPPING, ExpenseCategory.ISLAND_SHIPPING,
            ExpenseCategory.DOMESTIC_SHIPPING, ExpenseCategory.PAYMENT_FEE, ExpenseCategory.TAX), ExpenseCategory.fixedCategories)
        ExpenseCategory.fixedCategories.forEach {
            assertTrue(ExpenseEditorState(category = it, amountText = "100", currency = "JPY").canSave)
        }
        assertFalse(ExpenseEditorState(category = ExpenseCategory.OTHER, amountText = "100").canSave)
        assertTrue(ExpenseEditorState(editingId = 1, name = "历史自定义费用", category = ExpenseCategory.OTHER, amountText = "100").canSave)
    }
    private val base = ExpenseEditorState(name = "Ship", amountText = "100", currency = "JPY", mode = AllocationMode.MANUAL)

    @Test fun `非法输入不允许保存或静默丢弃`() {
        for (text in listOf("abc", "-1", "1.5", "101", "9223372036854775808")) {
            assertFalse("input=$text", base.copy(manualInputs = mapOf(1L to text)).canSave)
        }
        assertFalse(base.copy(manualInputs = mapOf(1L to "60", 2L to "50")).canSave)
        assertFalse(base.copy(manualInputs = mapOf(1L to Long.MAX_VALUE.toString(), 2L to "1")).canSave)
    }

    @Test fun `空白明确为零部分分配可保存并保留数值`() {
        val form = base.copy(manualInputs = mapOf(1L to "40", 2L to ""))
        assertTrue(form.canSave)
        assertEquals(mapOf(1L to 40L, 2L to 0L), form.parsedManual)
        assertTrue(base.copy(manualInputs = mapOf(1L to "100")).canSave)
    }
}
