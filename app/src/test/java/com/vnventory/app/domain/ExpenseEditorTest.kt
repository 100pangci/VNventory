package com.vnventory.app.domain

import com.vnventory.app.domain.model.*
import com.vnventory.app.ui.orders.ExpenseEditorState
import org.junit.Assert.*
import org.junit.Test

class ExpenseEditorTest {
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
